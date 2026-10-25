import Foundation
import Observation

public enum LinkState: Int {
    case disconnected = 0
    case connecting = 1
    case pairing = 2
    case connected = 3
    case failed = 4
}

/// Owns the link to one watch and turns protocol traffic into observable app state.
@Observable public final class WatchSession {
    public private(set) var state: LinkState = .disconnected
    public private(set) var statusText: String = "Not connected"
    public private(set) var watch = WatchInfo()
    public private(set) var battery: BatteryStatus? = nil
    public private(set) var faces: [WatchFace] = []
    public private(set) var quickReplies: [String] = []
    public private(set) var live: LiveData? = nil
    public private(set) var isSyncing = false
    public private(set) var syncProgress = 0.0
    public private(set) var syncMessage = ""
    public private(set) var lastSync: Date? = nil
    public private(set) var isFindingPhone = false
    public private(set) var needsRebind = false
    public private(set) var nowPlaying = NowPlaying()
    public private(set) var knownWatches: [DiscoveredWatch] = []
    public private(set) var logLines: [String] = []
    public private(set) var transportKind = ""

    @ObservationIgnored public let health: HealthStore
    @ObservationIgnored public let preferences: PreferencesStore
    @ObservationIgnored public let phone: PhoneIntegration
    @ObservationIgnored private var transport: WatchTransport
    @ObservationIgnored private let parser = BerryStreamParser()
    @ObservationIgnored private var serial = 0
    @ObservationIgnored private var outbox: [BerryCommand] = []
    @ObservationIgnored private var awaitingAck = false
    @ObservationIgnored private var sendToken = 0
    @ObservationIgnored private var pendingIds: [FitnessRecordID] = []
    @ObservationIgnored private var userDisconnected = false
    @ObservationIgnored private var reconnectAttempts = 0
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var lastProgressAt = Date()
    @ObservationIgnored private var callNotificationKey = ""

    public init(health: HealthStore, preferences: PreferencesStore, phone: PhoneIntegration) {
        self.health = health
        self.preferences = preferences
        self.phone = phone
        self.transport = preferences.value.demoMode ? DemoTransport() : TransportFactory.platformDefault()
        self.transportKind = transport.kind
        wireTransport()
        wirePhone()
    }

    private var prefs: Preferences {
        return preferences.value
    }

    public var isConnected: Bool {
        return state == .connected
    }

    public var displayName: String {
        if let w = prefs.pairedWatch {
            return w.name
        }
        return "Watch"
    }

    // MARK: - Lifecycle

    public func start() {
        phone.start()
        refreshKnownWatches()
        if prefs.pairedWatch != nil && state == .disconnected {
            reconnect()
        }
    }

    public func setDemoMode(_ enabled: Bool) {
        disconnect()
        preferences.value.demoMode = enabled
        if enabled {
            preferences.value.pairedWatch = DemoTransport().knownWatches().first
        } else if preferences.value.pairedWatch?.address == DemoTransport.address {
            preferences.value.pairedWatch = nil
        }
        transport = enabled ? DemoTransport() : TransportFactory.platformDefault()
        transportKind = transport.kind
        wireTransport()
        refreshKnownWatches()
        if enabled {
            reconnect()
        }
    }

    public func refreshKnownWatches() {
        let found = transport.knownWatches()
        knownWatches = found.sorted { a, b in
            if a.looksLikeNoiseWatch != b.looksLikeNoiseWatch {
                return a.looksLikeNoiseWatch
            }
            return a.name < b.name
        }
    }

    public func connect(to watch: DiscoveredWatch) {
        preferences.value.pairedWatch = watch
        reconnectAttempts = 0
        reconnect()
    }

    public func reconnect() {
        guard let target = prefs.pairedWatch else { return }
        userDisconnected = false
        generation += 1
        parser.reset()
        resetOutbox()
        setState(.connecting, "Connecting to \(target.name)…")
        transport.connect(address: target.address)
    }

    public func disconnect() {
        userDisconnected = true
        generation += 1
        transport.disconnect()
        phone.linkDown()
        setState(.disconnected, "Disconnected")
    }

    public func forgetWatch() {
        disconnect()
        preferences.value.pairedWatch = nil
        watch = WatchInfo()
        battery = nil
        faces = []
        live = nil
        setState(.disconnected, "Not connected")
    }

    private func setState(_ newState: LinkState, _ text: String) {
        state = newState
        statusText = text
        log("state: \(text)")
    }

    private func wireTransport() {
        let link = transport
        transport.onReceive = { bytes in
            // Reassemble on the transport's reader thread so byte order is preserved, and ACK immediately.
            let units = self.parser.feed(bytes)
            for unit in units where unit.kind == BerryInboundKind.packet {
                _ = link.send(BerryFrame.ack(serial: unit.serial))
            }
            if !units.isEmpty {
                Task { @MainActor in
                    self.handle(units)
                }
            }
        }
        transport.onStateChange = { newState, message in
            Task { @MainActor in
                self.transportChanged(newState, message)
            }
        }
    }

    private func transportChanged(_ newState: TransportState, _ message: String) {
        if newState == .connected {
            reconnectAttempts = 0
            setState(.pairing, "Securing connection…")
            send(BerryCommands.hello())
        } else if newState == .connecting {
            if state != .connecting {
                setState(.connecting, message)
            }
        } else {
            let wasConnected = state == .connected
            isSyncing = false
            resetOutbox()
            phone.linkDown()
            if userDisconnected {
                setState(.disconnected, "Disconnected")
                return
            }
            setState(newState == .failed ? .failed : .disconnected, message)
            if prefs.autoReconnect && prefs.pairedWatch != nil {
                scheduleReconnect(after: wasConnected ? 3 : nil)
            }
        }
    }

    private func scheduleReconnect(after seconds: Int?) {
        reconnectAttempts += 1
        let delay = seconds ?? min(60, 5 * reconnectAttempts)
        let expected = generation
        statusText = statusText + " · retrying in \(delay)s"
        Task { @MainActor in
            await Delay.ms(delay * 1000)
            if self.generation == expected && !self.userDisconnected && self.state != .connected {
                self.reconnect()
            }
        }
    }

    // MARK: - Outbound

    /// Queues a command. The watch drops frames that arrive while it is still processing the previous one,
    /// so like the vendor SDK only one request is in flight; the next goes out on ACK (or after a timeout).
    public func send(_ command: BerryCommand) {
        outbox.append(command)
        if !awaitingAck {
            pumpOutbox()
        }
    }

    private func pumpOutbox() {
        guard !outbox.isEmpty else {
            awaitingAck = false
            return
        }
        let command = outbox.removeFirst()
        let frame = BerryFrame.encode(serial: serial, event: command.event, type: command.type, payload: command.payload)
        serial = (serial + 1) & 0xFF
        let ok = transport.send(frame)
        log("→ \(command.label) \(ok ? "" : "(not sent)")")
        awaitingAck = true
        sendToken += 1
        let token = sendToken
        Task { @MainActor in
            await Delay.ms(800)
            if self.awaitingAck && self.sendToken == token {
                self.pumpOutbox()
            }
        }
    }

    private func resetOutbox() {
        outbox = []
        awaitingAck = false
        sendToken += 1
    }

    // MARK: - Inbound

    private func handle(_ units: [BerryInbound]) {
        for unit in units {
            switch unit.kind {
            case .ack:
                if awaitingAck {
                    pumpOutbox()
                }
            case .packet:
                if unit.event == BerryFrame.eventPrivate {
                    handlePrivate(unit.payload)
                } else if unit.event == BerryFrame.eventProtobuf {
                    if let message = WearMessage.parse(unit.payload) {
                        handleWear(message)
                    }
                }
            case .largeFileProgress:
                lastProgressAt = Date()
                if unit.total > 0 {
                    syncProgress = 0.2 + 0.7 * Double(unit.received) / Double(unit.total)
                    syncMessage = "Receiving health data…"
                }
            case .largeFile:
                handleFile(type: unit.type, data: unit.payload)
            }
        }
    }

    private func handlePrivate(_ payload: [UInt8]) {
        let cmd = BerryResponses.privateCommand(payload)
        if cmd.cmdId == BerryPrivateID.hello {
            let info = BerryResponses.protocolInfo(cmd.data)
            log("protocol \(info.protocolVersion) device \(info.deviceType)")
            watch.model = "\(info.deviceType)"
            send(BerryCommands.userId(prefs.accountId, phoneName: phone.phoneName, systemVersion: phone.systemVersion))
        } else if cmd.cmdId == BerryPrivateID.userId {
            let bind = BerryResponses.bindInfo(cmd.data)
            log("bound \(bind.isBound) sameUser \(bind.isSameUser) fw \(bind.firmware)")
            if bind.isBound && bind.isSameUser {
                needsRebind = false
                ready()
            } else if !bind.isBound {
                setState(.pairing, "Confirm pairing on your watch")
                send(BerryCommands.bindRequest())
            } else {
                needsRebind = true
                setState(.failed, "This watch is paired to another account. Use the NoiseFit account ID or reset the watch.")
            }
        } else if cmd.cmdId == BerryPrivateID.bindRequest {
            let status = cmd.data.isEmpty ? 1 : Int(cmd.data[0])
            if status == 0 || status == 3 {
                send(BerryCommands.bindConfirm(true))
            } else {
                setState(.failed, "Pairing was declined on the watch")
            }
        } else if cmd.cmdId == BerryPrivateID.bindConfirm {
            needsRebind = false
            ready()
        }
    }

    /// Ask the watch to bind to this app's account (it may require confirming on the watch or a reset).
    public func requestRebind() {
        needsRebind = false
        setState(.pairing, "Confirm pairing on your watch")
        send(BerryCommands.bindRequest())
    }

    private func ready() {
        setState(.connected, "Connected")
        if let w = prefs.pairedWatch {
            watch.name = w.name
            watch.address = w.address
            phone.linkUp(watchName: w.name)
        }
        send(BerryCommands.battery())
        send(BerryCommands.deviceInfo())
        send(BerryCommands.setTime(Date(), timeZone: TimeZone.current))
        send(BerryCommands.setUserProfile(prefs.profile))
        send(BerryCommands.quickReplies())
        send(BerryCommands.watchFaces())
        send(BerryCommands.get(WearID.getHeartRateMonitor))
        send(BerryCommands.get(WearID.getSpO2Settings))
        send(BerryCommands.get(WearID.getStressMode))
        send(BerryCommands.get(WearID.getDoNotDisturb))
        send(BerryCommands.get(WearID.getWristScreen))
        send(BerryCommands.get(WearID.getSedentary))
        send(BerryCommands.get(WearID.getDrinkWater))
        send(BerryCommands.get(WearID.getAlarms))
        if phone.supportsMediaControl {
            send(BerryCommands.musicInfo(nowPlaying))
        }
        send(BerryCommands.liveData(true))
        let expected = generation
        Task { @MainActor in
            await Delay.ms(1500)
            if self.generation == expected && self.isConnected {
                self.syncHealth()
            }
            self.runPeriodicTasks(expected)
        }
    }

    private func runPeriodicTasks(_ expected: Int) {
        Task { @MainActor in
            var minutes = 0
            while self.generation == expected && self.isConnected {
                await Delay.ms(60_000)
                minutes += 1
                if self.generation != expected || !self.isConnected { break }
                if minutes % 10 == 0 {
                    self.send(BerryCommands.battery())
                }
                if minutes % 30 == 0 {
                    self.syncHealth()
                }
                if self.isSyncing && Date().timeIntervalSince(self.lastProgressAt) > 60 {
                    self.finishSync("Sync timed out")
                }
            }
        }
    }

    private func handleWear(_ m: WearMessage) {
        logger.debug("← \(m.type)/\(m.id)")
        if m.isError {
            log("← \(m.type)/\(m.id) error \(m.errorCode)")
            if m.type == WearType.settingMenu && (m.id == WearID.getFitnessIdList || m.id == WearID.appControlFileSendStatus) {
                finishSync("Watch could not send health data (code \(m.errorCode))")
            }
            return
        }
        if m.type == WearType.device {
            handleDevice(m)
        } else if m.type == WearType.settingMenu {
            handleSettingMenu(m)
        } else if m.type == WearType.notification {
            handleNotification(m)
        } else if m.type == WearType.music {
            handleMusic(m)
        } else if m.type == WearType.microFunction {
            handleMicroFunction(m)
        } else if m.type == WearType.watchFace {
            if m.id == WearID.getWatchFaces, let body = m.body(WearField.watchFace) {
                faces = BerryResponses.watchFaces(body)
            } else if m.id == WearID.watchChangedFace || m.id == WearID.setWatchFace || m.id == WearID.removeWatchFace {
                send(BerryCommands.watchFaces())
            }
        }
    }

    private func handleDevice(_ m: WearMessage) {
        guard let device = m.body(WearField.device) else { return }
        if let info = device.message(1) {
            var w = watch
            if let b = BerryResponses.deviceInfo(info, into: &w) {
                battery = b
            }
            watch = w
        }
        if let b = device.message(2) {
            battery = BerryResponses.battery(b)
        }
    }

    private func handleSettingMenu(_ m: WearMessage) {
        guard let menu = m.body(WearField.settingMenu) else { return }
        if m.id == WearID.getFitnessIdList {
            let ids = FitnessDecoder.parseIdList(menu)
            if ids.isEmpty {
                finishSync("Up to date")
                return
            }
            pendingIds = ids
            syncProgress = 0.2
            syncMessage = "Receiving \(ids.count) records…"
            lastProgressAt = Date()
            send(BerryCommands.requestFitnessFile(fileType: BerryFileType.daily, ids: ids))
        } else if m.id == WearID.deviceControlFileSendStatus {
            if let status = menu.message(57), (status.int(2) ?? 0) == 1, (status.int(1) ?? 0) == BerryFileType.daily {
                if prefs.deleteAfterSync && !pendingIds.isEmpty {
                    send(BerryCommands.deleteFitnessIds(fileType: BerryFileType.daily, ids: pendingIds))
                }
                pendingIds = []
                finishSync("Synced")
            }
        } else if m.id == WearID.getHeartRateMonitor, let s = menu.message(3) {
            preferences.value.heartRate = BerryResponses.heartRate(s)
        } else if m.id == WearID.getSpO2Settings, let s = menu.message(30) {
            preferences.value.spo2 = BerryResponses.spo2(s)
        } else if m.id == WearID.getStressMode, let s = menu.message(28) {
            preferences.value.stress = BerryResponses.stress(s)
        } else if m.id == WearID.getDoNotDisturb, let s = menu.message(8) {
            preferences.value.doNotDisturb = BerryResponses.doNotDisturb(s)
        } else if m.id == WearID.getWristScreen, let s = menu.message(1) {
            preferences.value.raiseToWake = BerryResponses.raiseToWake(s)
        } else if m.id == WearID.getSedentary, let s = menu.message(4) {
            preferences.value.sedentary = BerryResponses.reminder(s)
        } else if m.id == WearID.getDrinkWater, let s = menu.message(7) {
            preferences.value.drinkWater = BerryResponses.reminder(s)
        } else if m.id == WearID.getAlarms, let s = menu.message(5) {
            preferences.value.alarms = BerryResponses.alarms(s)
        }
    }

    private func handleNotification(_ m: WearMessage) {
        let body = m.body(WearField.notification)
        if m.id == WearID.getQuickReplies, let b = body {
            quickReplies = BerryResponses.quickReplies(b)
        } else if m.id == WearID.sendCallState, let b = body, b.has(4) {
            phone.callAction(b.int(4) ?? 1)
        } else if m.id == WearID.sendQuickReplyMessage, let b = body, let reply = b.message(6) {
            let sent = phone.sendSMS(to: reply.string(1) ?? "", text: reply.string(2) ?? "")
            log("quick reply \(sent ? "sent" : "not sent")")
        }
    }

    private func handleMusic(_ m: WearMessage) {
        if m.id == WearID.sendMusicCommand, let body = m.body(WearField.music), let raw = body.int(2), let command = MediaCommand(rawValue: raw) {
            phone.mediaCommand(command)
        } else if m.id == WearID.requestMusicState {
            send(BerryCommands.musicInfo(nowPlaying))
        }
    }

    private func handleMicroFunction(_ m: WearMessage) {
        let body = m.body(WearField.microFunction)
        if m.id == WearID.wearFindPhone {
            let mode = body?.int(1) ?? 0
            if mode == 0 {
                isFindingPhone = true
                phone.startFindPhone()
            } else {
                isFindingPhone = false
                phone.stopFindPhone()
            }
        } else if m.id == WearID.phoneBasicDataReport || m.id == WearID.reportBasicData, let b = body, let data = b.message(4) {
            // The watch answers the start request with id 4, then pushes updates with id 5.
            live = BerryResponses.liveData(data)
            log("live: \(live?.steps ?? 0) steps")
            if let status = data.message(7) {
                battery = BerryResponses.battery(status)
            }
        }
    }

    // MARK: - Health sync

    public func syncHealth() {
        guard isConnected, !isSyncing else { return }
        isSyncing = true
        syncProgress = 0.05
        syncMessage = "Checking for new data…"
        lastProgressAt = Date()
        send(BerryCommands.fitnessIdList(fileType: BerryFileType.daily))
    }

    private func handleFile(type: Int, data: [UInt8]) {
        guard type == BerryFileType.daily else { return }
        var days = health.days
        let count = FitnessDecoder.decode(file: data, ids: pendingIds, into: &days)
        health.replace(with: days)
        syncProgress = 0.95
        syncMessage = "Saved \(count) records"
        log("decoded \(count) of \(pendingIds.count) records")
    }

    private func finishSync(_ message: String) {
        isSyncing = false
        syncProgress = 1.0
        syncMessage = message
        if message == "Synced" || message == "Up to date" {
            lastSync = Date()
        }
        log("sync: \(message)")
    }

    // MARK: - Phone → watch relays

    private func wirePhone() {
        phone.onNotification = { n in
            Task { @MainActor in
                self.forward(n)
            }
        }
        phone.onCallEnded = {
            Task { @MainActor in
                if self.isConnected && !self.callNotificationKey.isEmpty {
                    self.callNotificationKey = ""
                    self.send(BerryCommands.callState(1))
                }
            }
        }
        phone.onNowPlaying = { info in
            Task { @MainActor in
                if info != self.nowPlaying {
                    self.nowPlaying = info
                    if self.isConnected {
                        self.send(BerryCommands.musicInfo(info))
                    }
                }
            }
        }
    }

    private func forward(_ n: PhoneNotification) {
        guard isConnected else { return }
        switch n.kind {
        case .incomingCall:
            guard prefs.forwardCalls, n.key != callNotificationKey else { return }
            callNotificationKey = n.key
            send(BerryCommands.systemNotification(kind: 0, number: n.text, contact: n.title, message: "", appName: n.appName, packageName: n.packageName))
        case .missedCall:
            guard prefs.forwardCalls else { return }
            send(BerryCommands.systemNotification(kind: 1, number: n.text, contact: n.title, message: n.text, appName: n.appName, packageName: n.packageName))
        case .sms:
            guard prefs.forwardMessages else { return }
            send(BerryCommands.systemNotification(kind: 2, number: n.title, contact: n.title, message: n.text, appName: n.appName, packageName: n.packageName))
        case .app:
            guard prefs.forwardNotifications, !prefs.mutedPackages.contains(n.packageName) else { return }
            send(BerryCommands.appNotification(appName: n.appName, packageName: n.packageName, title: n.title, text: n.text, key: n.key))
        }
    }

    /// Sends a sample notification so the user can verify forwarding.
    public func sendTestNotification() {
        send(BerryCommands.appNotification(appName: "SilentFit", packageName: "com.hariharan.silentfit", title: "SilentFit", text: "Notifications are working.", key: "silentfit-test"))
    }

    // MARK: - User actions

    public func findWatch() {
        send(BerryCommands.findWatch())
    }

    public func stopFindingPhone() {
        isFindingPhone = false
        phone.stopFindPhone()
        send(BerryCommands.phoneFoundStop())
    }

    public func refreshBattery() {
        send(BerryCommands.battery())
    }

    public func syncTime() {
        send(BerryCommands.setTime(Date(), timeZone: TimeZone.current))
    }

    public func requestLiveData() {
        send(BerryCommands.liveData(true))
    }

    public func setWatchFace(_ face: WatchFace) {
        send(BerryCommands.setWatchFace(face.id))
    }

    public func removeWatchFace(_ face: WatchFace) {
        send(BerryCommands.removeWatchFace(face.id))
    }

    public func refreshWatchFaces() {
        send(BerryCommands.watchFaces())
    }

    public func saveQuickReplies(_ replies: [String]) {
        quickReplies = replies
        send(BerryCommands.setQuickReplies(replies))
    }

    public func updateProfile(_ profile: UserProfile) {
        preferences.value.profile = profile
        if isConnected {
            send(BerryCommands.setUserProfile(profile))
            send(BerryCommands.setUnits(metric: profile.metric))
        }
    }

    public func updateHeartRate(_ s: HeartRateSettings) {
        preferences.value.heartRate = s
        send(BerryCommands.setHeartRateMonitor(s))
    }

    public func updateSpO2(_ s: SpO2Settings) {
        preferences.value.spo2 = s
        send(BerryCommands.setSpO2(s))
    }

    public func updateStress(_ s: StressSettings) {
        preferences.value.stress = s
        send(BerryCommands.setStress(s))
    }

    public func updateDoNotDisturb(_ s: ScheduleSettings) {
        preferences.value.doNotDisturb = s
        send(BerryCommands.setDoNotDisturb(s))
    }

    public func updateRaiseToWake(_ s: ScheduleSettings) {
        preferences.value.raiseToWake = s
        send(BerryCommands.setRaiseToWake(s))
    }

    public func updateSedentary(_ s: ReminderSettings) {
        preferences.value.sedentary = s
        send(BerryCommands.setSedentary(s))
    }

    public func updateDrinkWater(_ s: ReminderSettings) {
        preferences.value.drinkWater = s
        send(BerryCommands.setDrinkWater(s))
    }

    public func updateAlarms(_ alarms: [WatchAlarm]) {
        preferences.value.alarms = alarms
        send(BerryCommands.setAlarms(alarms))
    }

    // MARK: - Diagnostics

    private func log(_ line: String) {
        let stamp = Date().formatted(date: .omitted, time: .standard)
        var lines = logLines
        lines.append("\(stamp)  \(line)")
        if lines.count > 300 {
            lines.removeFirst(lines.count - 300)
        }
        logLines = lines
        logger.debug("\(line)")
    }

    public func clearLog() {
        logLines = []
    }
}
