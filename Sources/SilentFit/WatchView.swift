import SwiftUI
import SilentFitKit

struct WatchView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @State private var showPairing = false
    @State private var confirmForget = false
    @State private var foundMessage = ""

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Watch")
                .sheet(isPresented: $showPairing) {
                    PairingView()
                }
        }
    }

    @ViewBuilder private var content: some View {
        if preferences.value.pairedWatch == nil {
            EmptyStateView(title: "No Watch Paired", symbol: "applewatch", message: "Pair your Noise watch with this device in Bluetooth settings, then choose it here.", actionTitle: "Pair Watch") {
                showPairing = true
            }
        } else {
            List {
                headerSection
                actionsSection
                customizeSection
                monitoringSection
                remindersSection
                detailsSection
                manageSection
            }
            .refreshable {
                session.refreshBattery()
                session.syncHealth()
            }
        }
    }

    // MARK: Sections

    private var headerSection: some View {
        Section {
            HStack(spacing: 16) {
                WatchHero(connected: session.isConnected, size: 72)
                VStack(alignment: .leading, spacing: 6) {
                    Text(session.displayName)
                        .font(.title3.weight(.semibold))
                    StatusPill(state: session.state, text: session.statusText)
                    if session.battery != nil {
                        BatteryLabel(battery: session.battery)
                    }
                }
            }
            .padding(.vertical, 6)
            if session.needsRebind {
                Button {
                    session.requestRebind()
                } label: {
                    row("Pair With SilentFit", symbol: "lock.shield.fill", color: Color.orange)
                }
            } else if session.state == .disconnected || session.state == .failed {
                Button {
                    session.reconnect()
                } label: {
                    row("Connect", symbol: "dot.radiowaves.left.and.right", color: Color.green)
                }
            }
            if session.isSyncing {
                VStack(alignment: .leading, spacing: 6) {
                    ProgressView(value: session.syncProgress)
                    Text(session.syncMessage)
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
            }
        }
    }

    private var actionsSection: some View {
        Section {
            Button {
                session.findWatch()
            } label: {
                row("Find My Watch", symbol: "applewatch.radiowaves.left.and.right", color: Color.green)
            }
            Button {
                session.syncHealth()
            } label: {
                row("Sync Health Data", symbol: "arrow.triangle.2.circlepath", color: Color.blue, detail: session.isSyncing ? "Syncing…" : Format.relative(session.lastSync))
            }
            Button {
                session.syncTime()
            } label: {
                row("Sync Time", symbol: "clock.fill", color: Color.gray)
            }
            Button {
                session.sendTestNotification()
            } label: {
                row("Send Test Notification", symbol: "bell.badge.fill", color: Color.red)
            }
        } header: {
            Text("Actions")
        } footer: {
            Text("Find My Watch makes your watch vibrate and ring.")
        }
        .disabled(!session.isConnected)
    }

    private var customizeSection: some View {
        Section("Customize") {
            NavigationLink {
                WatchFacesView()
            } label: {
                row("Watch Faces", symbol: "square.grid.2x2.fill", color: Color.indigo, detail: currentFaceName)
            }
            NavigationLink {
                QuickRepliesView()
            } label: {
                row("Quick Replies", symbol: "text.bubble.fill", color: Color.green, detail: "\(session.quickReplies.count)")
            }
            NavigationLink {
                AlarmsView()
            } label: {
                row("Alarms", symbol: "alarm.fill", color: Color.orange, detail: "\(preferences.value.alarms.filter { $0.enabled }.count) on")
            }
        }
    }

    private var monitoringSection: some View {
        Section("Health Monitoring") {
            NavigationLink {
                HeartRateSettingsView()
            } label: {
                row("Heart Rate", symbol: "heart.fill", color: Color.red, detail: preferences.value.heartRate.enabled ? "Every \(preferences.value.heartRate.intervalMinutes) min" : "Off")
            }
            NavigationLink {
                SpO2SettingsView()
            } label: {
                row("Blood Oxygen", symbol: "lungs.fill", color: Color.cyan, detail: preferences.value.spo2.enabled ? "On" : "Off")
            }
            NavigationLink {
                StressSettingsView()
            } label: {
                row("Stress", symbol: "brain.head.profile", color: Color.purple, detail: preferences.value.stress.enabled ? "On" : "Off")
            }
        }
    }

    private var remindersSection: some View {
        Section("Reminders & Display") {
            NavigationLink {
                ScheduleSettingsView(title: "Raise to Wake", footer: "Turn on the screen when you raise your wrist, during the selected hours.", initial: preferences.value.raiseToWake) { s in
                    session.updateRaiseToWake(s)
                }
            } label: {
                row("Raise to Wake", symbol: "sun.max.fill", color: Color.yellow, detail: preferences.value.raiseToWake.enabled ? "On" : "Off")
            }
            NavigationLink {
                ScheduleSettingsView(title: "Do Not Disturb", footer: "Silence notifications and calls on your watch during these hours.", initial: preferences.value.doNotDisturb) { s in
                    session.updateDoNotDisturb(s)
                }
            } label: {
                row("Do Not Disturb", symbol: "moon.fill", color: Color.indigo, detail: preferences.value.doNotDisturb.enabled ? "\(Format.clock(minutes: preferences.value.doNotDisturb.startMinutes))–\(Format.clock(minutes: preferences.value.doNotDisturb.endMinutes))" : "Off")
            }
            NavigationLink {
                ReminderSettingsView(title: "Move Reminders", footer: "Get a gentle nudge when you've been inactive for too long.", initial: preferences.value.sedentary) { s in
                    session.updateSedentary(s)
                }
            } label: {
                row("Move Reminders", symbol: "chair.fill", color: Color.teal, detail: preferences.value.sedentary.enabled ? "On" : "Off")
            }
            NavigationLink {
                ReminderSettingsView(title: "Hydration", footer: "Reminders to drink water through the day.", initial: preferences.value.drinkWater) { s in
                    session.updateDrinkWater(s)
                }
            } label: {
                row("Hydration", symbol: "drop.fill", color: Color.blue, detail: preferences.value.drinkWater.enabled ? "On" : "Off")
            }
        }
    }

    private var detailsSection: some View {
        Section("About This Watch") {
            LabeledContent("Name", value: session.displayName)
            LabeledContent("Model", value: session.watch.model.isEmpty ? "--" : session.watch.model)
            LabeledContent("Firmware", value: session.watch.firmware.isEmpty ? "--" : session.watch.firmware)
            LabeledContent("Bluetooth Address", value: preferences.value.pairedWatch?.address ?? "--")
            LabeledContent("Connection", value: session.transportKind)
        }
    }

    private var manageSection: some View {
        Section {
            if session.isConnected {
                Button("Disconnect") {
                    session.disconnect()
                }
            }
            Button("Change Watch") {
                showPairing = true
            }
            Button("Forget This Watch", role: .destructive) {
                confirmForget = true
            }
            .confirmationDialog("Forget this watch?", isPresented: $confirmForget, titleVisibility: .visible) {
                Button("Forget Watch", role: .destructive) {
                    session.forgetWatch()
                }
            } message: {
                Text("Health history stays on this device. You can pair the watch again at any time.")
            }
        }
    }

    private var currentFaceName: String {
        for face in session.faces where face.isCurrent {
            return face.name
        }
        return ""
    }

    private func row(_ title: String, symbol: String, color: Color, detail: String = "") -> some View {
        HStack(spacing: 12) {
            IconBadge(symbol: symbol, color: color)
            Text(title)
                .foregroundStyle(Color.primary)
            Spacer()
            if !detail.isEmpty {
                Text(detail)
                    .foregroundStyle(Color.secondary)
                    .lineLimit(1)
            }
        }
        .accessibilityGroup()
    }
}

struct PairingView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        WatchHero(connected: false, size: 56)
                        Text("Choose Your Watch")
                            .font(.title2.weight(.bold))
                        Text(instructions)
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                    }
                    .padding(.vertical, 6)
                }
                Section {
                    if session.knownWatches.isEmpty {
                        Text("No paired watches found.")
                            .foregroundStyle(Color.secondary)
                    }
                    ForEach(session.knownWatches) { watch in
                        Button {
                            session.connect(to: watch)
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                IconBadge(symbol: watch.looksLikeNoiseWatch ? "applewatch" : "dot.radiowaves.left.and.right", color: watch.looksLikeNoiseWatch ? Color.accentColor : Color.gray)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(watch.name)
                                        .foregroundStyle(Color.primary)
                                    Text(watch.address)
                                        .font(.caption)
                                        .foregroundStyle(Color.secondary)
                                }
                                Spacer()
                                if preferences.value.pairedWatch?.address == watch.address {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(Color.accentColor)
                                }
                            }
                        }
                    }
                } header: {
                    Text("Paired Devices")
                } footer: {
                    Text("Close NoiseFit on this phone first — only one app can talk to the watch at a time.")
                }
                Section {
                    Button {
                        session.refreshKnownWatches()
                    } label: {
                        Label("Refresh List", systemImage: "arrow.clockwise")
                    }
                    #if os(Android)
                    Button {
                        session.phone.openBluetoothSettings()
                    } label: {
                        Label("Open Bluetooth Settings", systemImage: "gearshape.fill")
                    }
                    #endif
                    Button {
                        session.setDemoMode(true)
                        dismiss()
                    } label: {
                        Label("Try the Demo Watch", systemImage: "sparkles")
                    }
                }
            }
            .navigationTitle("Pair Watch")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                session.refreshKnownWatches()
            }
        }
    }

    private var instructions: String {
        #if os(Android)
        return "Your watch must already be paired in Android Bluetooth settings. Noise watches appear as names like “NF 2_A1B2”."
        #elseif os(macOS)
        return "Pair your watch with this Mac in System Settings › Bluetooth, then choose it below."
        #else
        return "SilentFit can reach Noise watches that support Bluetooth LE. Classic-only models such as the NF 2 can't be reached from iPhone — see the user guide."
        #endif
    }
}

struct WatchFacesView: View {
    @Environment(WatchSession.self) var session

    var body: some View {
        List {
            if session.faces.isEmpty {
                EmptyStateView(title: "No Watch Faces", symbol: "square.grid.2x2.fill", message: session.isConnected ? "Pull to refresh." : "Connect your watch to manage faces.")
            }
            ForEach(session.faces) { face in
                Button {
                    session.setWatchFace(face)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(face.name)
                                .foregroundStyle(Color.primary)
                            if face.canRemove {
                                Text("Installed")
                                    .font(.caption)
                                    .foregroundStyle(Color.secondary)
                            }
                        }
                        Spacer()
                        if face.isCurrent {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Color.accentColor)
                                .accessibilityLabel("Current")
                        }
                    }
                }
                .swipeActions(edge: .trailing) {
                    if face.canRemove && !face.isCurrent {
                        Button(role: .destructive) {
                            session.removeWatchFace(face)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .navigationTitle("Watch Faces")
        .refreshable {
            session.refreshWatchFaces()
        }
        .onAppear {
            if session.isConnected {
                session.refreshWatchFaces()
            }
        }
    }
}
