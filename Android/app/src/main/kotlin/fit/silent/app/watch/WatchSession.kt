package fit.silent.app.watch

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import fit.silent.app.data.AppSettings
import fit.silent.app.data.FaceTrial
import fit.silent.app.data.HealthRepository
import fit.silent.app.data.JsonStore
import fit.silent.app.data.PairedWatch
import fit.silent.app.phone.PhoneNotification
import fit.silent.app.phone.PhoneServices
import fit.silent.core.fitness.FitnessDecoder
import fit.silent.core.model.AlwaysOnSettings
import fit.silent.core.model.BatteryStatus
import fit.silent.core.model.FitnessRecordId
import fit.silent.core.model.HealthDay
import fit.silent.core.model.HeartRateSettings
import fit.silent.core.model.InstalledFace
import fit.silent.core.model.LauncherItem
import fit.silent.core.model.LauncherList
import fit.silent.core.model.LiveData
import fit.silent.core.model.MediaCommand
import fit.silent.core.model.NowPlaying
import fit.silent.core.model.RaiseToWakeSettings
import fit.silent.core.model.ReminderKind
import fit.silent.core.model.ReminderSettings
import fit.silent.core.model.ScheduleSettings
import fit.silent.core.model.ScreenSettings
import fit.silent.core.model.SleepModeSettings
import fit.silent.core.model.SpO2Settings
import fit.silent.core.model.StressSettings
import fit.silent.core.model.UserProfile
import fit.silent.core.model.WatchAlarm
import fit.silent.core.model.WatchInfo
import fit.silent.core.model.WatchNotificationSettings
import fit.silent.core.model.WatchSettings
import fit.silent.core.model.WorkoutDetection
import fit.silent.core.protocol.BerryCommand
import fit.silent.core.protocol.BerryFrame
import fit.silent.core.protocol.BerryInbound
import fit.silent.core.protocol.BerryStreamParser
import fit.silent.core.protocol.CommandPolicy
import fit.silent.core.protocol.Commands
import fit.silent.core.protocol.DeviceId
import fit.silent.core.protocol.EditResponse
import fit.silent.core.protocol.FaceId
import fit.silent.core.protocol.FileType
import fit.silent.core.protocol.InboundKind
import fit.silent.core.protocol.InstallResult
import fit.silent.core.protocol.LargeFilePlan
import fit.silent.core.protocol.MenuField
import fit.silent.core.protocol.MenuId
import fit.silent.core.protocol.MicroId
import fit.silent.core.protocol.MusicId
import fit.silent.core.protocol.NotificationId
import fit.silent.core.protocol.PrepareReply
import fit.silent.core.protocol.PrivateId
import fit.silent.core.protocol.ProtoMessage
import fit.silent.core.protocol.Responses
import fit.silent.core.protocol.UserSettingId
import fit.silent.core.protocol.WearField
import fit.silent.core.protocol.WearMessage
import fit.silent.core.protocol.WearType
import fit.silent.core.watchface.FaceSafety
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.random.Random

enum class LinkPhase { DISCONNECTED, CONNECTING, HANDSHAKE, PAIRING, CONNECTED, FAILED }

data class Connection(val phase: LinkPhase, val message: String, val retryAt: Long = 0) {
    val isConnected get() = phase == LinkPhase.CONNECTED
    val isBusy get() = phase == LinkPhase.CONNECTING || phase == LinkPhase.HANDSHAKE || phase == LinkPhase.PAIRING
}

data class SyncState(val running: Boolean = false, val progress: Float = 0f, val message: String = "")

data class TransferState(val title: String, val sent: Long, val total: Long, val phase: String) {
    val fraction get() = if (total <= 0) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)
}

sealed interface FaceInstall {
    data class Installed(val faceId: String) : FaceInstall
    /** [needsSpace]: the watch is full; retry with `freeSpace = true` to replace its removable faces. */
    data class Failed(val reason: String, val needsSpace: Boolean = false) : FaceInstall
}

/**
 * One Berry-protocol conversation with the paired watch.
 *
 * All protocol state lives on a single serial dispatcher. Bytes are parsed and acknowledged on the link's
 * reader thread so ordering is preserved, then handled here. Outgoing commands are paced: one in flight
 * until the watch's ACK (or 800 ms), because the firmware drops frames that arrive in a burst.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchSession(
    private val context: Context,
    private val settings: JsonStore<AppSettings>,
    private val health: HealthRepository,
    private val phone: PhoneServices,
    appScope: CoroutineScope,
) {
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob() + serial)

    private val _connection = MutableStateFlow(Connection(LinkPhase.DISCONNECTED, "Not connected"))
    private val _watch = MutableStateFlow(WatchInfo())
    private val _battery = MutableStateFlow<BatteryStatus?>(null)
    private val _live = MutableStateFlow<LiveData?>(null)
    private val _faces = MutableStateFlow<List<InstalledFace>>(emptyList())
    private val _faceSlots = MutableStateFlow<List<Int>>(emptyList())
    private val _settings = MutableStateFlow(WatchSettings())
    private val _replies = MutableStateFlow<List<String>>(emptyList())
    private val _sync = MutableStateFlow(SyncState())
    private val _transfer = MutableStateFlow<TransferState?>(null)
    private val _findingPhone = MutableStateFlow(false)
    private val _log = MutableStateFlow<List<String>>(emptyList())

    val connection: StateFlow<Connection> = _connection.asStateFlow()
    val watch: StateFlow<WatchInfo> = _watch.asStateFlow()
    val battery: StateFlow<BatteryStatus?> = _battery.asStateFlow()
    val live: StateFlow<LiveData?> = _live.asStateFlow()
    val faces: StateFlow<List<InstalledFace>> = _faces.asStateFlow()
    val faceSlots: StateFlow<List<Int>> = _faceSlots.asStateFlow()
    val watchSettings: StateFlow<WatchSettings> = _settings.asStateFlow()
    val quickReplies: StateFlow<List<String>> = _replies.asStateFlow()
    val sync: StateFlow<SyncState> = _sync.asStateFlow()
    val transfer: StateFlow<TransferState?> = _transfer.asStateFlow()
    val findingPhone: StateFlow<Boolean> = _findingPhone.asStateFlow()
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var link: WatchLink = newLink()
    private val parser = BerryStreamParser()
    private var outbox = Channel<Outgoing>(Channel.UNLIMITED)
    private val acks = Channel<Int>(Channel.UNLIMITED)
    private var nextSerial = 0
    private var generation = 0
    private var userDisconnected = false
    private var bluetoothOn = true
    private var attempts = 0
    private var reconnectJob: Job? = null
    private var periodicJob: Job? = null
    private var senderJob: Job? = null
    private var pendingIds: List<FitnessRecordId> = emptyList()
    private var lastProgressAt = 0L
    private var callKey = ""
    private var nowPlaying = NowPlaying()
    private var uiVisible = false
    @Volatile private var prepareReply: CompletableDeferred<PrepareReply>? = null
    @Volatile private var installResult: CompletableDeferred<InstallResult>? = null
    @Volatile private var editResponse: CompletableDeferred<EditResponse>? = null
    @Volatile private var bgResult: CompletableDeferred<Int>? = null
    @Volatile private var uploading = false
    @Volatile private var uploadAcks = 0

    private sealed interface Outgoing {
        class Cmd(val command: BerryCommand) : Outgoing
        class Exclusive(val block: suspend () -> Unit) : Outgoing
    }

    init {
        phone.onNotification = { n -> scope.launch { forward(n) } }
        phone.onCallEnded = { scope.launch { if (callKey.isNotEmpty() && isConnected) { callKey = ""; send(Commands.callState(1)) } } }
        phone.onNowPlaying = { info -> scope.launch { if (info != nowPlaying) { nowPlaying = info; if (isConnected) send(Commands.musicInfo(info)) } } }
    }

    private val prefs get() = settings.value
    val isConnected get() = _connection.value.isConnected

    private fun newLink(): WatchLink = if (settings.value.demoMode) DemoLink() else SppLink(context)

    // ================================================================ lifecycle

    fun connect() = scope.launch {
        val target = prefs.pairedWatch ?: return@launch
        if (_connection.value.isBusy || isConnected) return@launch
        userDisconnected = false
        open(target)
    }

    fun pair(watch: PairedWatch) = scope.launch {
        settings.update { it.copy(pairedWatch = watch) }
        attempts = 0
        closeLink()
        userDisconnected = false
        open(watch)
    }

    fun disconnect() = scope.launch {
        userDisconnected = true
        reconnectJob?.cancel()
        closeLink()
        setPhase(LinkPhase.DISCONNECTED, "Disconnected")
    }

    fun forget() = scope.launch {
        userDisconnected = true
        reconnectJob?.cancel()
        closeLink()
        settings.update { it.copy(pairedWatch = null) }
        endTrial("cleared: watch forgotten")
        _watch.value = WatchInfo(); _battery.value = null; _faces.value = emptyList(); _live.value = null; _settings.value = WatchSettings()
        setPhase(LinkPhase.DISCONNECTED, "Not connected")
    }

    fun setDemoMode(enabled: Boolean) = scope.launch {
        closeLink()
        settings.update { it.copy(demoMode = enabled, pairedWatch = if (enabled) PairedWatch("NF 2 (Demo)", DEMO_ADDRESS) else it.pairedWatch?.takeIf { w -> w.address != DEMO_ADDRESS }) }
        link = newLink()
        setPhase(LinkPhase.DISCONNECTED, "Not connected")
        if (enabled) { userDisconnected = false; prefs.pairedWatch?.let { open(it) } }
    }

    /** Bluetooth adapter switched; stop retrying while it is off and reconnect promptly when it returns. */
    fun onBluetoothChanged(on: Boolean) = scope.launch {
        bluetoothOn = on
        if (!on) {
            reconnectJob?.cancel()
            if (!prefs.demoMode) { closeLink(); setPhase(LinkPhase.DISCONNECTED, "Bluetooth is off") }
        } else if (!userDisconnected && prefs.autoReconnect && !isConnected) {
            attempts = 0
            scheduleReconnect(2)
        }
    }

    /** The watch's Bluetooth link came back (ACL connected) — connect now instead of waiting for backoff. */
    fun onWatchNearby(address: String) = scope.launch {
        if (address.equals(prefs.pairedWatch?.address, true) && !isConnected && !_connection.value.isBusy && !userDisconnected) {
            attempts = 0
            reconnectJob?.cancel()
            prefs.pairedWatch?.let { open(it) }
        }
    }

    fun setUiVisible(visible: Boolean) = scope.launch {
        if (uiVisible == visible) return@launch
        uiVisible = visible
        if (!isConnected) return@launch
        send(Commands.liveData(visible))
        if (visible && System.currentTimeMillis() - prefs.lastSync > 15 * 60_000) syncHealth()
    }

    private fun open(target: PairedWatch) {
        generation++
        reconnectJob?.cancel()
        parser.reset()
        outbox.close()
        outbox = Channel(Channel.UNLIMITED)
        while (acks.tryReceive().isSuccess) Unit
        val gen = generation
        if (!prefs.demoMode && !bluetoothOn) { setPhase(LinkPhase.DISCONNECTED, "Bluetooth is off"); return }
        if (!prefs.demoMode && !phone.hasBluetoothPermission) { setPhase(LinkPhase.FAILED, "Allow Nearby devices to connect"); return }
        link = newLink()
        _watch.update { it.copy(name = target.name, address = target.address) }
        setPhase(LinkPhase.CONNECTING, "Connecting to ${target.name}")
        link.open(target.address, object : fit.silent.app.watch.LinkListener {
            override fun onBytes(bytes: ByteArray, count: Int) {
                if (gen != generation) return
                val units = parser.feed(bytes, count)
                for (u in units) {
                    when (u.kind) {
                        InboundKind.PACKET -> link.write(BerryFrame.ack(u.serial))
                        InboundKind.ACK -> { acks.trySend(u.serial); if (uploading) uploadAcks++ }
                        else -> Unit
                    }
                }
                if (units.isNotEmpty()) scope.launch { if (gen == generation) units.forEach { handle(it) } }
            }

            override fun onState(state: LinkState, message: String) {
                scope.launch { if (gen == generation) linkChanged(state, message) }
            }
        })
    }

    private fun closeLink() {
        generation++
        periodicJob?.cancel()
        senderJob?.cancel()
        outbox.close()
        link.close()
        _sync.update { it.copy(running = false) }
        phone.stopFindPhone()
        _findingPhone.value = false
    }

    private fun linkChanged(state: LinkState, message: String) {
        when (state) {
            LinkState.CONNECTING -> setPhase(LinkPhase.CONNECTING, "Connecting to ${prefs.pairedWatch?.name ?: "watch"}")
            LinkState.CONNECTED -> {
                attempts = 0
                startSender()
                setPhase(LinkPhase.HANDSHAKE, "Securing connection")
                send(Commands.hello())
            }
            LinkState.DISCONNECTED, LinkState.FAILED -> {
                val wasConnected = isConnected
                periodicJob?.cancel()
                senderJob?.cancel()
                _sync.update { it.copy(running = false) }
                failPending("Watch disconnected")
                if (userDisconnected) { setPhase(LinkPhase.DISCONNECTED, "Disconnected"); return }
                if (wasConnected && prefs.faceTrial != null) { trialLinkDropped = true; log("face trial: link dropped") }
                setPhase(if (state == LinkState.FAILED) LinkPhase.FAILED else LinkPhase.DISCONNECTED, message)
                if (prefs.autoReconnect && prefs.pairedWatch != null && bluetoothOn) scheduleReconnect(if (wasConnected) 3 else null)
            }
        }
    }

    /** Backoff 5 s → 5 min; ACL and adapter broadcasts short-circuit the wait. */
    private fun scheduleReconnect(seconds: Int?) {
        attempts++
        val delaySeconds = seconds ?: BACKOFF.getOrElse(attempts - 1) { BACKOFF.last() }
        val at = System.currentTimeMillis() + delaySeconds * 1000L
        _connection.update { it.copy(retryAt = at) }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delaySeconds * 1000L)
            if (!userDisconnected && !isConnected) prefs.pairedWatch?.let { open(it) }
        }
    }

    private fun setPhase(phase: LinkPhase, message: String) {
        _connection.value = Connection(phase, message)
        log("state: $message")
    }

    // ================================================================ outgoing

    fun send(command: BerryCommand) { outbox.trySend(Outgoing.Cmd(command)) }

    private fun startSender() {
        senderJob?.cancel()
        val box = outbox
        senderJob = scope.launch {
            for (item in box) {
                when (item) {
                    is Outgoing.Cmd -> transmit(item.command)
                    is Outgoing.Exclusive -> item.block()
                }
            }
        }
    }

    /** Proven commands always; reconstructed ones only when the user opted into experimental controls. */
    fun permits(c: BerryCommand) = CommandPolicy.isProven(c) || prefs.experimentalWatchControls

    private suspend fun transmit(c: BerryCommand) {
        if (!permits(c)) { log("blocked ${c.label}: not proven on this watch"); return }
        while (acks.tryReceive().isSuccess) Unit
        val frame = BerryFrame.encode(nextSerial, c.event, c.type, c.payload)
        nextSerial = (nextSerial + 1) and 0xFF
        val ok = withContext(Dispatchers.IO) { link.write(frame) }
        log("→ ${c.label}${if (ok) "" else " (not sent)"}")
        if (ok) withTimeoutOrNull(ACK_TIMEOUT) { acks.receive() }
    }

    /** Runs [block] with exclusive use of the link (no other commands interleave). */
    private suspend fun <T> exclusive(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        outbox.send(Outgoing.Exclusive { runCatching { block() }.fold({ result.complete(it) }, { result.completeExceptionally(it) }) })
        return result.await()
    }

    private fun failPending(reason: String) {
        val e = IllegalStateException(reason)
        prepareReply?.completeExceptionally(e); installResult?.completeExceptionally(e)
        editResponse?.completeExceptionally(e); bgResult?.completeExceptionally(e)
    }

    // ================================================================ incoming

    private fun handle(u: BerryInbound) {
        when (u.kind) {
            InboundKind.PACKET -> when (u.event) {
                BerryFrame.EVENT_PRIVATE -> handlePrivate(u.payload)
                BerryFrame.EVENT_PROTOBUF -> WearMessage.parse(u.payload)?.let { handleWear(it) }
            }
            InboundKind.FILE_PROGRESS -> {
                lastProgressAt = System.currentTimeMillis()
                if (u.total > 0) _sync.update { it.copy(progress = 0.2f + 0.7f * u.received / u.total, message = "Receiving health data") }
            }
            InboundKind.FILE -> handleFile(u.type, u.payload)
            InboundKind.ACK -> Unit
        }
    }

    private fun handlePrivate(payload: ByteArray) {
        val reply = Responses.privateReply(payload)
        when (reply.cmdId) {
            PrivateId.HELLO -> {
                val info = Responses.protocolInfo(reply.data)
                log("protocol ${info.protocolVersion} device ${info.deviceType} platform ${info.platformType}")
                _watch.update { it.copy(deviceType = info.deviceType, platformType = info.platformType) }
                if (prefs.accountId.isEmpty()) settings.update { it.copy(accountId = fit.silent.app.data.LegacyImport.newAccountId()) }
                send(Commands.userId(prefs.accountId, phone.phoneName, phone.systemVersion))
            }
            PrivateId.USER_ID -> {
                val bind = Responses.bindInfo(reply.data)
                log("bound ${bind.isBound} sameUser ${bind.isSameUser} fw ${bind.firmware}")
                _watch.update { it.copy(firmware = bind.firmware) }
                when {
                    bind.isBound && bind.isSameUser -> ready()
                    !bind.isBound -> { setPhase(LinkPhase.PAIRING, "Confirm pairing on your watch"); send(Commands.bindRequest()) }
                    else -> setPhase(LinkPhase.FAILED, "Paired to another account — enter its account ID or reset the watch")
                }
            }
            PrivateId.BIND_REQUEST -> {
                val status = reply.data.firstOrNull()?.toInt() ?: 1
                if (status == 0 || status == 3) send(Commands.bindConfirm(true)) else setPhase(LinkPhase.FAILED, "Pairing was declined on the watch")
            }
            PrivateId.BIND_CONFIRM -> ready()
        }
    }

    /** Ask the watch to bind to this app's account (may need confirming on the watch). */
    fun requestRebind() = scope.launch {
        if (_connection.value.phase == LinkPhase.FAILED && link !is DemoLink) {
            setPhase(LinkPhase.PAIRING, "Confirm pairing on your watch")
            send(Commands.bindRequest())
        }
    }

    private fun ready() {
        setPhase(LinkPhase.CONNECTED, "Connected")
        // A face trial whose link dropped is undone first; otherwise it simply keeps being watched.
        resumeTrial()
        send(Commands.battery())
        send(Commands.deviceInfo())
        send(Commands.setTime(System.currentTimeMillis(), TimeZone.getDefault()))
        send(Commands.quickReplies())
        send(Commands.watchFaces())
        CommandPolicy.SETTING_GETS.forEach { send(Commands.get(it)) }
        if (prefs.experimentalWatchControls) {
            val p = prefs.profile
            send(Commands.setUserProfile(p))
            send(Commands.setUnits(p.metric))
            send(Commands.faceSupportData())
            LauncherList.entries.forEach { send(Commands.getLauncher(it)) }
            send(Commands.getLanguages())
        }
        send(Commands.musicInfo(nowPlaying))
        if (uiVisible) send(Commands.liveData(true))
        val gen = generation
        periodicJob?.cancel()
        periodicJob = scope.launch {
            delay(1500)
            if (gen == generation) syncHealth()
            var minutes = 0
            while (isActive && gen == generation && isConnected) {
                delay(60_000)
                minutes++
                if (minutes % 30 == 0) send(Commands.battery())
                val interval = prefs.syncIntervalMinutes
                if (interval > 0 && minutes % interval == 0) syncHealth()
                if (_sync.value.running && System.currentTimeMillis() - lastProgressAt > 60_000) finishSync("Sync timed out")
            }
        }
    }

    private fun handleWear(m: WearMessage) {
        Log.d(TAG, "← ${m.type}/${m.id}${if (m.isError) " error ${m.errorCode}" else ""}")
        if (m.isError) {
            log("← ${m.type}/${m.id} error ${m.errorCode}")
            if (m.type == WearType.SETTING_MENU && (m.id == MenuId.FITNESS_IDS || m.id == MenuId.FILE_SEND_STATUS)) finishSync("Watch could not send health data (${m.errorCode})")
            if (m.type == WearType.WATCH_FACE && m.id == FaceId.PREPARE) prepareReply?.completeExceptionally(IllegalStateException("Watch refused the face (error ${m.errorCode})"))
            return
        }
        when (m.type) {
            WearType.DEVICE -> m.body(WearField.DEVICE)?.let { d ->
                d.message(1)?.let { info ->
                    _watch.update { Responses.deviceInfo(info, it) }
                    info.message(5)?.let { _battery.value = Responses.battery(it) }
                }
                d.message(2)?.let { _battery.value = Responses.battery(it) }
            }
            WearType.SETTING_MENU -> m.body(WearField.SETTING_MENU)?.let { handleMenu(m.id, it) } ?: if (MenuId.isWatchChange(m.id)) send(Commands.get(MenuId.getFor(m.id))) else Unit
            WearType.NOTIFICATION -> handleNotification(m)
            WearType.MUSIC -> when (m.id) {
                MusicId.COMMAND -> m.body(WearField.MUSIC)?.int(2)?.let { MediaCommand.of(it) }?.let { phone.media(it) }
                MusicId.REQUEST_STATE -> send(Commands.musicInfo(nowPlaying))
            }
            WearType.MICRO -> handleMicro(m)
            WearType.WATCH_FACE -> handleFace(m)
            WearType.USER_SETTINGS -> m.body(WearField.USER_SETTINGS)?.message(2)?.let { langs ->
                _settings.update { it.copy(languages = langs.message(1)?.ints(1).orEmpty(), language = langs.int(3)) }
            }
        }
    }

    private fun handleMenu(id: Int, menu: ProtoMessage) {
        if (MenuId.isWatchChange(id) && !menu.fields.any()) { send(Commands.get(MenuId.getFor(id))); return }
        when (id) {
            MenuId.FITNESS_IDS -> {
                val ids = FitnessDecoder.parseIdList(menu)
                if (ids.isEmpty()) { finishSync("Up to date"); return }
                pendingIds = ids
                lastProgressAt = System.currentTimeMillis()
                _sync.value = SyncState(true, 0.2f, "Receiving ${ids.size} records")
                send(Commands.requestFitnessFile(FileType.DAILY, ids))
            }
            MenuId.DEVICE_FILE_STATUS -> menu.message(MenuField.DEVICE_FILE_STATUS)?.let { s ->
                if ((s.int(2) ?: 0) == 1 && (s.int(1) ?: 0) == FileType.DAILY) {
                    if (prefs.deleteAfterSync && pendingIds.isNotEmpty()) send(Commands.deleteFitnessIds(FileType.DAILY, pendingIds))
                    pendingIds = emptyList()
                    finishSync("Synced")
                }
            }
            MenuId.HEART_RATE -> menu.message(MenuField.HEART_RATE)?.let { s -> _settings.update { it.copy(heartRate = Responses.heartRate(s)) } }
            MenuId.SPO2 -> menu.message(MenuField.SPO2)?.let { s -> _settings.update { it.copy(spo2 = Responses.spo2(s)) } }
            MenuId.STRESS -> menu.message(MenuField.STRESS)?.let { s -> _settings.update { it.copy(stress = Responses.stress(s)) } }
            MenuId.DND -> menu.message(MenuField.DND)?.let { s -> _settings.update { it.copy(doNotDisturb = Responses.doNotDisturb(s)) } }
            MenuId.WRIST -> menu.message(MenuField.WRIST)?.let { s -> _settings.update { it.copy(raiseToWake = Responses.raiseToWake(s)) } }
            MenuId.ALARMS -> menu.message(MenuField.ALARMS)?.let { s -> _settings.update { it.copy(alarms = Responses.alarms(s), maxAlarms = s.int(2) ?: it.maxAlarms) } }
            MenuId.VIBRATION -> menu.int(MenuField.VIBRATION)?.let { v -> _settings.update { it.copy(vibration = v) } }
            MenuId.POWER_SAVING -> menu.message(MenuField.POWER_SAVING)?.let { s -> _settings.update { it.copy(powerSaving = s.bool(1) ?: false) } }
            MenuId.COVER -> menu.message(MenuField.COVER)?.let { s -> _settings.update { it.copy(coverToMute = s.bool(1) ?: false) } }
            MenuId.SCREEN -> menu.message(MenuField.SCREEN)?.let { s -> _settings.update { it.copy(screen = Responses.screen(s)) } }
            MenuId.AOD -> menu.message(MenuField.AOD)?.let { s -> _settings.update { it.copy(alwaysOn = Responses.alwaysOn(s)) } }
            MenuId.SLEEP_MODE -> menu.message(MenuField.SLEEP_MODE)?.let { s -> _settings.update { it.copy(sleepMode = Responses.sleepMode(s)) } }
            MenuId.NOTIFY -> menu.message(MenuField.NOTIFY)?.let { s -> _settings.update { it.copy(notifications = Responses.watchNotifications(s)) } }
            MenuId.WORKOUT -> menu.message(MenuField.WORKOUT)?.let { s -> _settings.update { it.copy(workoutDetection = Responses.workoutDetection(s)) } }
            MenuId.REM -> menu.message(MenuField.REM)?.let { s -> _settings.update { it.copy(remSleep = s.bool(1) ?: false) } }
            else -> ReminderKind.entries.firstOrNull { Commands.reminderIds(it).first == id }?.let { kind ->
                menu.message(Commands.reminderIds(kind).second)?.let { s -> _settings.update { it.copy(reminders = it.reminders + (kind to Responses.reminder(s))) } }
            } ?: if (MenuId.isWatchChange(id)) send(Commands.get(MenuId.getFor(id))) else Unit
        }
    }

    private fun handleNotification(m: WearMessage) {
        val body = m.body(WearField.NOTIFICATION)
        when (m.id) {
            NotificationId.GET_REPLIES -> body?.let { _replies.value = Responses.quickReplies(it) }
            NotificationId.CALL_STATE -> body?.takeIf { it.has(4) }?.let { phone.callAction(it.int(4) ?: 1) }
            NotificationId.REPLY_MESSAGE -> body?.message(6)?.let { r ->
                val sent = phone.sendSms(r.string(1).orEmpty(), r.string(2).orEmpty())
                log("quick reply ${if (sent) "sent" else "not sent"}")
            }
        }
    }

    private fun handleMicro(m: WearMessage) {
        val body = m.body(WearField.MICRO)
        when (m.id) {
            MicroId.WEAR_FIND_PHONE -> {
                if ((body?.int(1) ?: 0) == 0) { _findingPhone.value = true; phone.startFindPhone() } else { _findingPhone.value = false; phone.stopFindPhone() }
            }
            MicroId.BASIC_DATA_REQUEST, MicroId.BASIC_DATA_REPORT -> body?.message(4)?.let { data ->
                _live.value = Responses.liveData(data, System.currentTimeMillis())
                data.message(7)?.let { _battery.value = Responses.battery(it) }
            }
            MicroId.GET_WIDGETS, MicroId.GET_APPS, MicroId.GET_QUICK -> {
                val (list, field) = when (m.id) { MicroId.GET_WIDGETS -> LauncherList.WIDGETS to 5; MicroId.GET_APPS -> LauncherList.APPS to 6; else -> LauncherList.QUICK to 7 }
                body?.message(field)?.let { l -> _settings.update { it.copy(launcher = it.launcher + (list to Responses.launcher(l))) } }
            }
        }
    }

    private fun handleFace(m: WearMessage) {
        val body = m.body(WearField.WATCH_FACE)
        when (m.id) {
            FaceId.LIST -> body?.let { _faces.value = Responses.watchFaces(it) }
            FaceId.SUPPORT_DATA -> body?.let { _faceSlots.value = Responses.faceSupportData(it) }
            FaceId.PREPARE -> body?.let { Responses.prepareReply(it) }?.let { prepareReply?.complete(it) }
            FaceId.INSTALL_RESULT -> body?.let { Responses.installResult(it) }?.let { installResult?.complete(it); send(Commands.watchFaces()) }
            FaceId.EDIT -> body?.let { Responses.editResponse(it) }?.let { editResponse?.complete(it) }
            FaceId.BG_RESULT -> bgResult?.complete(body?.message(14)?.int(1) ?: 0)
            FaceId.SET, FaceId.REMOVE, FaceId.WATCH_CHANGED -> send(Commands.watchFaces())
        }
    }

    // ================================================================ health sync

    fun syncHealth() = scope.launch {
        if (!isConnected || _sync.value.running) return@launch
        lastProgressAt = System.currentTimeMillis()
        _sync.value = SyncState(true, 0.05f, "Checking for new data")
        send(Commands.fitnessIdList(FileType.DAILY))
    }

    private fun handleFile(type: Int, data: ByteArray) {
        if (type != FileType.DAILY) return
        val days = HashMap<String, HealthDay>()
        pendingIds.map { it.dayKey }.distinct().forEach { key -> health.archive.value.days[key]?.let { days[key] = it } }
        val count = FitnessDecoder.decode(data, pendingIds, days)
        health.merge(days)
        _sync.update { it.copy(progress = 0.95f, message = "Saved $count records") }
        log("decoded $count of ${pendingIds.size} records")
    }

    private fun finishSync(message: String) {
        _sync.value = SyncState(false, 1f, message)
        if (message == "Synced" || message == "Up to date") settings.update { it.copy(lastSync = System.currentTimeMillis()) }
        log("sync: $message")
    }

    // ================================================================ phone relays

    private fun forward(n: PhoneNotification) {
        if (!isConnected) return
        val p = prefs
        if (p.quietWhileScreenOn && phoneInUse()) return
        when (n.kind) {
            PhoneNotification.Kind.INCOMING_CALL -> {
                if (!p.forwardCalls || n.key == callKey) return
                callKey = n.key
                send(Commands.systemNotification(0, n.text, n.title, "", n.appName, n.packageName))
            }
            PhoneNotification.Kind.MISSED_CALL -> if (p.forwardCalls) send(Commands.systemNotification(1, n.text, n.title, n.text, n.appName, n.packageName))
            PhoneNotification.Kind.SMS -> if (p.forwardMessages) send(Commands.systemNotification(2, n.title, n.title, n.text, n.appName, n.packageName))
            PhoneNotification.Kind.APP -> if (p.forwardNotifications && n.packageName !in p.mutedPackages) send(Commands.appNotification(n.appName, n.packageName, n.title, n.text, n.key))
        }
    }

    private fun phoneInUse(): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    fun sendTestNotification() = send(Commands.appNotification("SilentFit", context.packageName, "SilentFit", "Notifications are working.", "silentfit-test"))

    // ================================================================ user actions

    fun findWatch() = send(Commands.findWatch())

    fun stopFindingPhone() = scope.launch {
        _findingPhone.value = false
        phone.stopFindPhone()
        if (isConnected) send(Commands.phoneFoundStop())
    }

    fun refreshBattery() = send(Commands.battery())
    fun syncTime() = send(Commands.setTime(System.currentTimeMillis(), TimeZone.getDefault()))
    fun refreshFaces() = send(Commands.watchFaces())

    /** Switching to a downloaded face runs as a trial, like a fresh install. */
    fun setFace(id: String) = scope.launch {
        val target = _faces.value.firstOrNull { it.id == id }
        if (target != null && target.canRemove) startTrial(id) else send(Commands.setWatchFace(id))
    }

    fun removeFace(id: String) = scope.launch {
        if (_faces.value.any { it.id == id && it.isCurrent }) { log("not removing the current face $id"); return@launch }
        send(Commands.removeWatchFace(id))
    }

    // ---------------------------------------------------------------- face trial watchdog

    private val _notice = MutableStateFlow<String?>(null)
    /** One-off message for the UI (e.g. a face trial was undone). */
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    private val _trial = MutableStateFlow(settings.value.faceTrial)
    val faceTrial: StateFlow<FaceTrial?> = _trial.asStateFlow()
    private var trialJob: Job? = null
    /** The watch dropped the link unexpectedly while a trial was running (what a crash looks like). */
    private var trialLinkDropped = false

    /**
     * Makes [faceId] current and watches the link. The trial passes after [TRIAL_MS] of connected time without an
     * unexpected drop; if the watch drops the link first (a crash reboots it), the next connection switches back to a
     * built-in face and removes [faceId].
     */
    private fun startTrial(faceId: String) {
        // Fall back to a built-in face: downloaded faces may just have been removed to make room.
        val previous = _faces.value.firstOrNull { it.isCurrent && !it.canRemove }?.id
            ?: _faces.value.firstOrNull { !it.canRemove }?.id
        if (previous == null) { log("face trial skipped: no fallback face known"); send(Commands.setWatchFace(faceId)); return }
        val trial = FaceTrial(faceId, previous, System.currentTimeMillis())
        settings.update { it.copy(faceTrial = trial) }
        _trial.value = trial
        trialLinkDropped = false
        send(Commands.setWatchFace(faceId))
        log("face trial: $faceId (fallback $previous)")
        watchTrial()
    }

    /** Polls instead of one long delay, so reconnects or a busy link can never leave a trial stuck. */
    private fun watchTrial() {
        trialJob?.cancel()
        trialJob = scope.launch {
            var connectedMs = 0L
            while (isActive) {
                val trial = prefs.faceTrial ?: break
                if (trialLinkDropped) break
                if (isConnected) connectedMs += TRIAL_TICK
                val current = _faces.value.firstOrNull { it.isCurrent }?.id
                when {
                    connectedMs >= TRIAL_MS -> { endTrial("passed: ${trial.faceId}"); break }
                    // The user picked another face on the watch or in the app: nothing left to protect.
                    current != null && current != trial.faceId && connectedMs >= 15_000 -> { endTrial("ended: ${current} is now current"); break }
                }
                delay(TRIAL_TICK)
            }
        }
    }

    private fun endTrial(reason: String) {
        trialJob?.cancel()
        trialLinkDropped = false
        if (prefs.faceTrial != null) settings.update { it.copy(faceTrial = null) }
        _trial.value = null
        log("face trial $reason")
    }

    /** Called when the link is ready: undo a trial whose link dropped, otherwise keep watching. */
    private fun resumeTrial() {
        val trial = prefs.faceTrial ?: run { _trial.value = null; return }
        _trial.value = trial
        if (trialLinkDropped) revertTrial(trial) else watchTrial()
    }

    private fun revertTrial(trial: FaceTrial) {
        log("face trial failed (link dropped): restoring ${trial.previousFaceId}, removing ${trial.faceId}")
        send(Commands.setWatchFace(trial.previousFaceId))
        send(Commands.removeWatchFace(trial.faceId))
        endTrial("reverted")
        _notice.value = "The watch disconnected while trying the new face, so SilentFit switched back to your previous face and removed the new one."
    }

    /** User confirms the face works before the trial window ends. */
    fun keepTrialFace() = scope.launch { endTrial("kept by user") }

    fun saveQuickReplies(replies: List<String>) {
        _replies.value = replies
        settings.update { it.copy(quickReplies = replies) }
        send(Commands.setQuickReplies(replies))
    }

    fun updateProfile(profile: UserProfile) {
        settings.update { it.copy(profile = profile) }
        if (isConnected && prefs.experimentalWatchControls) {
            send(Commands.setUserProfile(profile))
            send(Commands.setUnits(profile.metric))
            send(Commands.setTemperatureUnit(profile.celsius))
        }
    }

    private fun apply(command: BerryCommand, transform: (WatchSettings) -> WatchSettings) {
        if (!permits(command)) { log("blocked ${command.label}: enable experimental watch controls to change this"); return }
        _settings.update(transform)
        send(command)
    }

    fun setHeartRate(s: HeartRateSettings) = apply(Commands.setHeartRate(s)) { it.copy(heartRate = s) }
    fun setSpO2(s: SpO2Settings) = apply(Commands.setSpO2(s)) { it.copy(spo2 = s) }
    fun setStress(s: StressSettings) = apply(Commands.setStress(s)) { it.copy(stress = s) }
    fun setDoNotDisturb(s: ScheduleSettings) = apply(Commands.setDoNotDisturb(s)) { it.copy(doNotDisturb = s) }
    fun setRaiseToWake(s: RaiseToWakeSettings) = apply(Commands.setRaiseToWake(s)) { it.copy(raiseToWake = s) }
    fun setReminder(kind: ReminderKind, s: ReminderSettings) = apply(Commands.setReminder(kind, s)) { it.copy(reminders = it.reminders + (kind to s)) }
    fun setAlarms(alarms: List<WatchAlarm>) = apply(Commands.setAlarms(alarms)) { it.copy(alarms = alarms) }
    fun setVibration(level: Int) = apply(Commands.setVibration(level)) { it.copy(vibration = level) }
    fun setPowerSaving(on: Boolean) = apply(Commands.setPowerSaving(on)) { it.copy(powerSaving = on) }
    fun setCoverToMute(on: Boolean) = apply(Commands.setCoverToMute(on)) { it.copy(coverToMute = on) }
    fun setScreen(s: ScreenSettings) = apply(Commands.setScreen(s)) { it.copy(screen = s) }
    fun setAlwaysOn(s: AlwaysOnSettings) = apply(Commands.setAlwaysOn(s)) { it.copy(alwaysOn = s) }
    fun setSleepMode(s: SleepModeSettings) = apply(Commands.setSleepMode(s)) { it.copy(sleepMode = s) }
    fun setWatchNotifications(s: WatchNotificationSettings) = apply(Commands.setWatchNotifications(s)) { it.copy(notifications = s) }
    fun setWorkoutDetection(s: WorkoutDetection) = apply(Commands.setWorkoutDetection(s)) { it.copy(workoutDetection = s) }
    fun setRemSleep(on: Boolean) = apply(Commands.setRemSleep(on)) { it.copy(remSleep = on) }
    fun setLanguage(id: Int) = apply(Commands.setLanguage(id)) { it.copy(language = id) }
    fun setLauncher(list: LauncherList, items: List<LauncherItem>) = apply(Commands.setLauncher(list, items)) { it.copy(launcher = it.launcher + (list to items)) }

    // ================================================================ watch faces

    /**
     * Installs a "WF" file: PREPARE → watch reply with slice size → large-file upload → install result.
     * The NF 2 keeps one downloadable face; with [freeSpace] the removable faces are deleted first, which is
     * what NoiseFit does silently. [replaces] is removed afterwards when it is still present.
     */
    suspend fun installFace(bytes: ByteArray, name: String, makeCurrent: Boolean = false, replaces: String? = null, freeSpace: Boolean = false): FaceInstall {
        if (!isConnected) return FaceInstall.Failed("Connect your watch first")
        FaceSafety.check(bytes).firstOrNull()?.let { problem ->
            log("face blocked: $problem")
            return FaceInstall.Failed("Blocked for safety: $problem")
        }
        // A newer install replaces the face on trial, so the old trial has nothing left to protect.
        if (prefs.faceTrial != null) endTrial("superseded by a new install")
        val id = newFaceId()
        var lowStorage = false
        return try {
            exclusive {
                uploadFile(name, FileType.WATCH_FACE, bytes) {
                    var reply = prepare(id, bytes.size)
                    if ((reply.status == 3 || reply.status == 6) && freeSpace) {
                        val removable = _faces.value.filter { it.canRemove }
                        log("freeing space: removing ${removable.joinToString { it.id }}")
                        removable.forEach { transmit(Commands.removeWatchFace(it.id)) }
                        delay(1500)
                        reply = prepare(id, bytes.size)
                    }
                    if (reply.status == 3 || reply.status == 6) lowStorage = true
                    if (reply.status != 0) throw IllegalStateException(PREPARE_STATUS.getOrElse(reply.status) { "Watch is not ready (${reply.status})" })
                    installResult = CompletableDeferred()
                    reply.expectedSlice
                }
                _transfer.value = TransferState(name, bytes.size.toLong(), bytes.size.toLong(), "Installing")
                val result = withTimeoutOrNull(60_000) { installResult!!.await() } ?: throw IllegalStateException("No install confirmation from the watch")
                if (!result.success) throw IllegalStateException(if (result.code == 0) "The watch rejected the file (verification failed)" else "Install failed on the watch")
            }
            if (replaces != null && replaces != id && _faces.value.any { it.id == replaces && !it.isCurrent }) send(Commands.removeWatchFace(replaces))
            if (makeCurrent) scope.launch { startTrial(id) }
            send(Commands.watchFaces())
            log("face $id installed (${bytes.size} bytes)")
            FaceInstall.Installed(id)
        } catch (e: Exception) {
            log("face install failed: ${e.message}")
            FaceInstall.Failed(e.message ?: "Install failed", lowStorage)
        } finally {
            _transfer.value = null
            prepareReply = null; installResult = null
        }
    }

    private suspend fun prepare(id: String, size: Int): PrepareReply {
        prepareReply = CompletableDeferred()
        transmit(Commands.prepareFaceInstall(id, size))
        val reply = withTimeoutOrNull(15_000) { prepareReply!!.await() } ?: throw IllegalStateException("The watch did not answer")
        log("prepare $id: status ${reply.status}, slice ${reply.expectedSlice}")
        return reply
    }

    /**
     * Album/photo faces: sets the background of an editable face. [par] is the encoded PAR image
     * (watch resolution); colour/style edits are sent without an image when [par] is null.
     */
    suspend fun editFace(face: InstalledFace, par: ByteArray?, color: String? = null, style: String? = null, slots: List<Int>? = null): FaceInstall {
        if (!isConnected) return FaceInstall.Failed("Connect your watch first")
        if (!prefs.experimentalWatchControls) return FaceInstall.Failed("Editing faces is experimental — enable it in Settings first")
        return try {
            exclusive {
                editResponse = CompletableDeferred()
                bgResult = if (par != null) CompletableDeferred() else null
                val md5 = par?.let { MessageDigest.getInstance("MD5").digest(it).joinToString("") { b -> "%02x".format(b) } }
                transmit(Commands.editFace(face.id, true, md5, par?.size ?: 0, color, style, face.styleColorIndex, slots))
                val response = withTimeoutOrNull(15_000) { editResponse!!.await() } ?: throw IllegalStateException("The watch did not answer")
                if (!response.ok) throw IllegalStateException(if (response.code == 2) "Not enough storage on the watch" else "The watch rejected the change (${response.code})")
                if (par != null) {
                    uploadFile("Background", FileType.ALBUM_BACKGROUND, par) { response.expectedSlice }
                    withTimeoutOrNull(45_000) { bgResult!!.await() }?.let { if (it != 0) throw IllegalStateException("The watch could not use this image ($it)") }
                }
            }
            send(Commands.watchFaces())
            FaceInstall.Installed(face.id)
        } catch (e: Exception) {
            FaceInstall.Failed(e.message ?: "Edit failed")
        } finally {
            _transfer.value = null
            editResponse = null; bgResult = null
        }
    }

    /** Streams one large file after [prepare] returns the watch's expected slice length. Runs inside [exclusive]. */
    private suspend fun uploadFile(title: String, type: Int, bytes: ByteArray, prepare: suspend () -> Int) {
        _transfer.value = TransferState(title, 0, bytes.size.toLong(), "Preparing")
        val expected = prepare()
        while (acks.tryReceive().isSuccess) Unit
        val plan = LargeFilePlan(type, bytes, expected, nextSerial)
        log("upload $title: ${bytes.size} B, slice ${plan.sliceLength}, ${plan.chunks.size} chunks")
        uploadAcks = 0
        uploading = true
        try {
            var sent = 0L
            plan.chunks.forEachIndexed { i, chunk ->
                if (!isConnected) throw IllegalStateException("Watch disconnected during transfer")
                if (!withContext(Dispatchers.IO) { link.write(chunk) }) throw IllegalStateException("Transfer interrupted")
                sent += chunk.size
                // The vendor waits for an ACK after the first chunks, then streams with a bounded window.
                if (i < 4) withTimeoutOrNull(if (i == 0) 10_000 else 3_000) { acks.receive() }
                else while (((sent + LargeFilePlan.ACK_BLOCK - 1) / LargeFilePlan.ACK_BLOCK) - uploadAcks > ACK_WINDOW) {
                    if (withTimeoutOrNull(5_000) { acks.receive() } == null) break
                }
                _transfer.value = TransferState(title, (sent * bytes.size / plan.totalBytes), bytes.size.toLong(), "Sending")
            }
            nextSerial = (nextSerial + plan.checkpoints.coerceAtLeast(1)) and 0xFF
        } finally {
            uploading = false
        }
    }

    private fun newFaceId(): String {
        val used = _faces.value.map { it.id }.toSet()
        while (true) {
            val id = "9" + Random.nextInt(100_000, 999_999)
            if (id !in used) return id
        }
    }

    fun clearLog() { _log.value = emptyList() }

    private fun log(line: String) {
        Log.d(TAG, line)
        val stamp = LocalTime.now().format(STAMP)
        _log.update { (it + "$stamp  $line").takeLast(400) }
    }

    companion object {
        private const val TAG = "SilentFitSession"
        const val DEMO_ADDRESS = "00:00:00:00:DE:00"
        private const val ACK_TIMEOUT = 800L
        private const val ACK_WINDOW = 16
        private val BACKOFF = intArrayOf(5, 10, 20, 40, 60, 120, 300)
        private val STAMP = DateTimeFormatter.ofPattern("HH:mm:ss")
        const val TRIAL_MS = 3 * 60_000L
        private const val TRIAL_TICK = 5_000L
        private val PREPARE_STATUS = listOf(
            "Ready", "The watch is busy — try again in a moment", "This face is already installed", "Not enough storage on the watch",
            "Battery too low for a transfer", "The watch has a newer version of this face", "Not enough storage — remove a face first",
        )
    }
}
