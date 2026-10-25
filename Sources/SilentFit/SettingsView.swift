import SwiftUI
import SilentFitKit

struct SettingsView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @State private var confirmClear = false
    @State private var showAccountHelp = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    NavigationLink {
                        ProfileView()
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: "person.crop.circle.fill")
                                .font(.system(size: 44))
                                .foregroundStyle(Color.accentColor)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(preferences.value.profile.name.isEmpty ? "Your Profile" : preferences.value.profile.name)
                                    .font(.headline)
                                Text("Goals, body measurements and units")
                                    .font(.subheadline)
                                    .foregroundStyle(Color.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                notificationsSection

                Section {
                    Toggle(isOn: Binding(get: { preferences.value.autoReconnect }, set: { preferences.value.autoReconnect = $0 })) {
                        settingLabel("Reconnect Automatically", symbol: "antenna.radiowaves.left.and.right", color: Color.green)
                    }
                    Toggle(isOn: Binding(get: { preferences.value.deleteAfterSync }, set: { preferences.value.deleteAfterSync = $0 })) {
                        settingLabel("Clear Watch After Sync", symbol: "arrow.triangle.2.circlepath", color: Color.blue)
                    }
                } header: {
                    Text("Sync")
                } footer: {
                    Text("Clearing synced records frees space on the watch, matching the original app's behavior. Your history is kept in SilentFit.")
                }

                Section {
                    HStack {
                        settingLabel("Watch Account ID", symbol: "number", color: Color.gray)
                        Spacer()
                        Button {
                            showAccountHelp = true
                        } label: {
                            Image(systemName: "info.circle")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("About the account ID")
                    }
                    TextField("Account ID", text: Binding(get: { preferences.value.accountId }, set: { preferences.value.accountId = $0 }))
                        .font(.body)
                        .monospaced()
                        .autocorrectionDisabled()
                        #if os(iOS) || os(Android)
                        .textInputAutocapitalization(.never)
                        #endif
                    Toggle(isOn: Binding(get: { preferences.value.demoMode }, set: { session.setDemoMode($0) })) {
                        settingLabel("Demo Watch", symbol: "sparkles", color: Color.purple)
                    }
                    NavigationLink {
                        LogView()
                    } label: {
                        settingLabel("Connection Log", symbol: "ladybug", color: Color.orange)
                    }
                } header: {
                    Text("Advanced")
                } footer: {
                    Text("The watch remembers which account it is bound to. Use the same ID as NoiseFit to switch apps without resetting the watch.")
                }

                Section {
                    Button("Clear Health History", role: .destructive) {
                        confirmClear = true
                    }
                    .confirmationDialog("Delete all health history stored in SilentFit?", isPresented: $confirmClear, titleVisibility: .visible) {
                        Button("Delete History", role: .destructive) {
                            session.health.clear()
                        }
                    }
                }

                Section("About") {
                    LabeledContent("Version", value: appVersion)
                    NavigationLink {
                        AboutView()
                    } label: {
                        settingLabel("About SilentFit", symbol: "doc.text", color: Color.gray)
                    }
                }
            }
            .navigationTitle("Settings")
            .alert("Watch Account ID", isPresented: $showAccountHelp) {
                Button("OK", role: .cancel) {
                }
            } message: {
                Text("Noise watches bind to an account ID. If your watch says it's paired to another account, enter the ID NoiseFit used (see the User Guide), or reset the watch and pair it with SilentFit.")
            }
        }
    }

    @ViewBuilder private var notificationsSection: some View {
        Section {
            Toggle(isOn: Binding(get: { preferences.value.forwardNotifications }, set: { preferences.value.forwardNotifications = $0 })) {
                settingLabel("App Notifications", symbol: "bell.badge.fill", color: Color.red)
            }
            Toggle(isOn: Binding(get: { preferences.value.forwardCalls }, set: { preferences.value.forwardCalls = $0 })) {
                settingLabel("Calls", symbol: "phone.fill", color: Color.green)
            }
            Toggle(isOn: Binding(get: { preferences.value.forwardMessages }, set: { preferences.value.forwardMessages = $0 })) {
                settingLabel("Text Messages", symbol: "message.fill", color: Color.green)
            }
            if session.phone.supportsNotificationForwarding {
                Button {
                    session.phone.openNotificationAccessSettings()
                } label: {
                    HStack {
                        settingLabel("Notification Access", symbol: "lock.shield.fill", color: Color.blue)
                        Spacer()
                        Text(session.phone.notificationAccessGranted ? "Allowed" : "Off")
                            .foregroundStyle(session.phone.notificationAccessGranted ? Color.secondary : Color.red)
                    }
                }
                #if os(Android)
                Button {
                    requestAndroidPermissions()
                } label: {
                    settingLabel("Bluetooth, Calls & SMS Permissions", symbol: "hand.raised.fill", color: Color.orange)
                }
                #endif
            }
        } header: {
            Text("Notifications")
        } footer: {
            Text(notificationFooter)
        }
    }

    private var notificationFooter: String {
        if session.phone.supportsNotificationForwarding {
            return "Notification access also lets your watch control music and see who's calling."
        }
        return "On iPhone and Mac, the system decides which notifications reach Bluetooth accessories."
    }

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        return version
    }

    private func settingLabel(_ title: String, symbol: String, color: Color) -> some View {
        HStack(spacing: 12) {
            IconBadge(symbol: symbol, color: color)
            Text(title)
                .foregroundStyle(Color.primary)
        }
    }
}

func requestAndroidPermissions() {
    #if SKIP
    if let activity = UIApplication.shared.androidActivity {
        PhoneBridge.requestPermissions(activity)
    }
    #endif
}

struct ProfileView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @State private var profile = UserProfile()
    @State private var loaded = false

    var body: some View {
        Form {
            Section("About You") {
                TextField("Name", text: $profile.name)
                Picker("Sex", selection: $profile.sex) {
                    Text("Not Set").tag(Sex.unspecified)
                    Text("Female").tag(Sex.female)
                    Text("Male").tag(Sex.male)
                }
                BoundedStepper(title: "Age: \(profile.age)", value: $profile.age, lower: 10, upper: 100)
                BoundedStepper(title: "Height: \(profile.heightCm) cm", value: $profile.heightCm, lower: 100, upper: 230)
                BoundedDoubleStepper(title: "Weight: \(Format.decimal(profile.weightKg, places: 1)) kg", value: $profile.weightKg, lower: 30.0, upper: 200.0, step: 0.5)
            }
            Section {
                BoundedStepper(title: "Steps: \(Format.grouped(profile.stepGoal))", value: $profile.stepGoal, lower: 1000, upper: 40000, step: 500)
                BoundedStepper(title: "Calories: \(profile.calorieGoal) kcal", value: $profile.calorieGoal, lower: 100, upper: 2000, step: 50)
                BoundedStepper(title: "Distance: \(Format.decimal(Double(profile.distanceGoalMeters) / 1000.0, places: 1)) km", value: $profile.distanceGoalMeters, lower: 1000, upper: 42000, step: 500)
                BoundedStepper(title: "Sleep: \(profile.sleepGoalHours) hours", value: $profile.sleepGoalHours, lower: 4, upper: 12)
            } header: {
                Text("Daily Goals")
            } footer: {
                Text("Goals are shown on your watch and in the activity rings.")
            }
            Section("Units") {
                Picker("Measurement System", selection: $profile.metric) {
                    Text("Metric").tag(true)
                    Text("Imperial").tag(false)
                }
            }
        }
        .navigationTitle("Profile")
        .onAppear {
            profile = preferences.value.profile
            loaded = true
        }
        .onChange(of: profile) { _, newValue in
            if loaded {
                session.updateProfile(newValue)
            }
        }
    }
}

struct LogView: View {
    @Environment(WatchSession.self) var session

    var body: some View {
        List {
            if session.logLines.isEmpty {
                Text("No events yet.")
                    .foregroundStyle(Color.secondary)
            }
            ForEach(0..<session.logLines.count, id: \.self) { i in
                Text(session.logLines[session.logLines.count - 1 - i])
                    .font(.caption)
                    .monospaced()
            }
        }
        .navigationTitle("Connection Log")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    session.clearLog()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
            }
        }
    }
}

struct AboutView: View {
    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("SilentFit")
                        .font(.title2.weight(.bold))
                    Text("A private, account-free companion for Noise smartwatches. Your health data never leaves your devices.")
                        .foregroundStyle(Color.secondary)
                }
                .padding(.vertical, 6)
            }
            Section("Disclaimer") {
                Text("SilentFit is an independent personal project and is not affiliated with or endorsed by Noise. Health readings are for general wellness and are not medical advice.")
                    .font(.footnote)
            }
            Section("Acknowledgements") {
                Text("Built with Skip (skip.dev). Android icons: Material Symbols by Google, Apache License 2.0. Apple platform icons: SF Symbols.")
                    .font(.footnote)
            }
        }
        .navigationTitle("About")
    }
}

struct OnboardingView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @State private var step = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer(minLength: 12)
                if step == 0 {
                    welcome
                } else {
                    permissions
                }
                Spacer(minLength: 12)
                Button {
                    advance()
                } label: {
                    Text(step == 0 ? "Continue" : "Get Started")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
            #if os(Android)
            .backDismissDisabled()
            #endif
        }
        .interactiveDismissDisabled()
    }

    private var welcome: some View {
        VStack(spacing: 20) {
            WatchHero(connected: true, size: 96)
            Text("Welcome to SilentFit")
                .font(.largeTitle.weight(.bold))
                .multilineTextAlignment(.center)
            VStack(alignment: .leading, spacing: 18) {
                feature("heart.fill", Color.red, "Your Health, Your Device", "Steps, heart rate, sleep, SpO₂ and stress — stored only on this device.")
                feature("bell.badge.fill", Color.orange, "Notifications & Calls", "See who's calling and read messages on your wrist.")
                feature("music.note", Color.pink, "Music Control", "Play, pause and skip tracks from your watch.")
                feature("lock.shield.fill", Color.green, "No Account Needed", "No sign-in, no cloud, no tracking.")
            }
            .padding(.horizontal, 28)
        }
    }

    private var permissions: some View {
        VStack(spacing: 20) {
            Image(systemName: "hand.raised.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.accentColor)
            Text("A Few Permissions")
                .font(.largeTitle.weight(.bold))
                .multilineTextAlignment(.center)
            VStack(alignment: .leading, spacing: 18) {
                feature("dot.radiowaves.left.and.right", Color.blue, "Bluetooth", "Required to connect to your watch.")
                if session.phone.supportsNotificationForwarding {
                    feature("bell.badge.fill", Color.red, "Notification Access", "Lets SilentFit forward notifications and control music. You can turn this on in Settings at any time.")
                    feature("phone.fill", Color.green, "Calls & SMS (Optional)", "Answer or decline calls and send quick replies from your watch.")
                }
            }
            .padding(.horizontal, 28)
        }
    }

    private func feature(_ symbol: String, _ color: Color, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(color)
                .frame(width: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
            }
        }
        .accessibilityGroup()
    }

    private func advance() {
        if step == 0 {
            step = 1
            requestAndroidPermissions()
            return
        }
        if session.phone.supportsNotificationForwarding && !session.phone.notificationAccessGranted {
            session.phone.openNotificationAccessSettings()
        }
        preferences.value.onboarded = true
    }
}
