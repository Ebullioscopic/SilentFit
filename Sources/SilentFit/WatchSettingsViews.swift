import SwiftUI
import SilentFitKit

/// Edits minutes-since-midnight with the platform time picker.
struct TimeField: View {
    let title: String
    @Binding var minutes: Int

    var body: some View {
        DatePicker(title, selection: dateBinding, displayedComponents: .hourAndMinute)
    }

    private var dateBinding: Binding<Date> {
        Binding(get: {
            let start = Calendar.current.startOfDay(for: Date())
            return start.addingTimeInterval(Double(minutes * 60))
        }, set: { date in
            let c = Calendar.current.dateComponents([.hour, .minute], from: date)
            minutes = (c.hour ?? 0) * 60 + (c.minute ?? 0)
        })
    }
}

struct HeartRateSettingsView: View {
    @Environment(WatchSession.self) var session
    @State private var settings = HeartRateSettings()
    @State private var loaded = false

    var body: some View {
        Form {
            Section {
                Toggle("Continuous Monitoring", isOn: $settings.enabled)
                if settings.enabled {
                    Picker("Measure Every", selection: $settings.intervalMinutes) {
                        Text("5 minutes").tag(5)
                        Text("10 minutes").tag(10)
                        Text("15 minutes").tag(15)
                        Text("30 minutes").tag(30)
                        Text("60 minutes").tag(60)
                    }
                }
            } footer: {
                Text("More frequent measurements give better charts but use more battery.")
            }
            Section {
                Toggle("High Heart Rate Alert", isOn: $settings.highAlertEnabled)
                if settings.highAlertEnabled {
                    BoundedStepper(title: "Above \(settings.highAlertBpm) BPM", value: $settings.highAlertBpm, lower: 100, upper: 200, step: 5)
                }
            } footer: {
                Text("Your watch alerts you when your heart rate stays above this level while you seem inactive.")
            }
        }
        .navigationTitle("Heart Rate")
        .onAppear {
            settings = session.preferences.value.heartRate
            loaded = true
        }
        .onChange(of: settings) { _, newValue in
            if loaded {
                session.updateHeartRate(newValue)
            }
        }
    }
}

struct SpO2SettingsView: View {
    @Environment(WatchSession.self) var session
    @State private var settings = SpO2Settings()
    @State private var loaded = false

    var body: some View {
        Form {
            Section {
                Toggle("Background Measurements", isOn: $settings.enabled)
                if settings.enabled {
                    Picker("Measure Every", selection: $settings.intervalMinutes) {
                        Text("10 minutes").tag(10)
                        Text("30 minutes").tag(30)
                        Text("60 minutes").tag(60)
                    }
                }
            } footer: {
                Text("Blood oxygen readings are for general wellness only and are not intended for medical use.")
            }
        }
        .navigationTitle("Blood Oxygen")
        .onAppear {
            settings = session.preferences.value.spo2
            loaded = true
        }
        .onChange(of: settings) { _, newValue in
            if loaded {
                session.updateSpO2(newValue)
            }
        }
    }
}

struct StressSettingsView: View {
    @Environment(WatchSession.self) var session
    @State private var settings = StressSettings()
    @State private var loaded = false

    var body: some View {
        Form {
            Section {
                Toggle("Stress Monitoring", isOn: $settings.enabled)
                Toggle("Relaxation Reminders", isOn: $settings.relaxReminder)
                    .disabled(!settings.enabled)
            } footer: {
                Text("When stress stays high, your watch can suggest a short breathing session.")
            }
        }
        .navigationTitle("Stress")
        .onAppear {
            settings = session.preferences.value.stress
            loaded = true
        }
        .onChange(of: settings) { _, newValue in
            if loaded {
                session.updateStress(newValue)
            }
        }
    }
}

struct ScheduleSettingsView: View {
    let title: String
    let footer: String
    let apply: (ScheduleSettings) -> Void
    @State private var settings: ScheduleSettings

    init(title: String, footer: String, initial: ScheduleSettings, apply: @escaping (ScheduleSettings) -> Void) {
        self.title = title
        self.footer = footer
        self.apply = apply
        self._settings = State(initialValue: initial)
    }

    var body: some View {
        Form {
            Section {
                Toggle(title, isOn: $settings.enabled)
                if settings.enabled {
                    TimeField(title: "From", minutes: $settings.startMinutes)
                    TimeField(title: "To", minutes: $settings.endMinutes)
                }
            } footer: {
                Text(footer)
            }
        }
        .navigationTitle(title)
        .onChange(of: settings) { _, newValue in
            apply(newValue)
        }
    }
}

struct ReminderSettingsView: View {
    let title: String
    let footer: String
    let apply: (ReminderSettings) -> Void
    @State private var settings: ReminderSettings

    init(title: String, footer: String, initial: ReminderSettings, apply: @escaping (ReminderSettings) -> Void) {
        self.title = title
        self.footer = footer
        self.apply = apply
        self._settings = State(initialValue: initial)
    }

    var body: some View {
        Form {
            Section {
                Toggle(title, isOn: $settings.enabled)
            } footer: {
                Text(footer)
            }
            if settings.enabled {
                Section("Schedule") {
                    TimeField(title: "From", minutes: $settings.startMinutes)
                    TimeField(title: "To", minutes: $settings.endMinutes)
                    Picker("Remind Every", selection: $settings.intervalMinutes) {
                        Text("30 minutes").tag(30)
                        Text("45 minutes").tag(45)
                        Text("1 hour").tag(60)
                        Text("90 minutes").tag(90)
                        Text("2 hours").tag(120)
                    }
                    Toggle("Pause Over Lunch", isOn: $settings.skipLunch)
                }
            }
        }
        .navigationTitle(title)
        .onChange(of: settings) { _, newValue in
            apply(newValue)
        }
    }
}

struct AlarmsView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @State private var editing: WatchAlarm? = nil

    var body: some View {
        List {
            if preferences.value.alarms.isEmpty {
                EmptyStateView(title: "No Alarms", symbol: "alarm.fill", message: "Alarms you add here vibrate on your watch.")
            }
            ForEach(preferences.value.alarms) { alarm in
                HStack {
                    Button {
                        editing = alarm
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(Format.clock(minutes: alarm.minutes))
                                .font(.system(size: 34, weight: .light))
                                .foregroundStyle(alarm.enabled ? Color.primary : Color.secondary)
                                .digits()
                            Text("\(alarm.label), \(AlarmEditor.repeatText(alarm.weekdayMask))")
                                .font(.subheadline)
                                .foregroundStyle(Color.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    Toggle("Enabled", isOn: enabledBinding(alarm))
                        .labelsHidden()
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        save(preferences.value.alarms.filter { $0.id != alarm.id })
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }
        }
        .navigationTitle("Alarms")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    editing = WatchAlarm()
                } label: {
                    Label("Add Alarm", systemImage: "plus")
                }
                .disabled(preferences.value.alarms.count >= 5)
            }
        }
        .sheet(item: $editing) { alarm in
            AlarmEditor(alarm: alarm) { updated in
                var list = preferences.value.alarms.filter { $0.id != updated.id }
                list.append(updated)
                save(list.sorted { $0.minutes < $1.minutes })
            }
        }
    }

    private func enabledBinding(_ alarm: WatchAlarm) -> Binding<Bool> {
        Binding(get: { alarm.enabled }, set: { value in
            var list: [WatchAlarm] = []
            for a in preferences.value.alarms {
                var copy = a
                if copy.id == alarm.id {
                    copy.enabled = value
                }
                list.append(copy)
            }
            save(list)
        })
    }

    private func save(_ alarms: [WatchAlarm]) {
        session.updateAlarms(alarms)
    }
}

struct AlarmEditor: View {
    let onSave: (WatchAlarm) -> Void
    @State private var alarm: WatchAlarm
    @Environment(\.dismiss) var dismiss

    static let dayNames = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    init(alarm: WatchAlarm, onSave: @escaping (WatchAlarm) -> Void) {
        self.onSave = onSave
        self._alarm = State(initialValue: alarm)
    }

    static func repeatText(_ mask: Int) -> String {
        if mask == 0 { return "Once" }
        if mask == 0x7F { return "Every day" }
        if mask == 0x1F { return "Weekdays" }
        if mask == 0x60 { return "Weekends" }
        var names: [String] = []
        for i in 0..<7 where (mask & (1 << i)) != 0 {
            names.append(dayNames[i])
        }
        return names.joined(separator: " ")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TimeField(title: "Time", minutes: $alarm.minutes)
                    TextField("Label", text: $alarm.label)
                }
                Section("Repeat") {
                    ForEach(0..<7, id: \.self) { i in
                        Toggle(AlarmEditor.dayNames[i], isOn: dayBinding(i))
                    }
                }
            }
            .navigationTitle("Alarm")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(alarm)
                        dismiss()
                    }
                }
            }
        }
    }

    private func dayBinding(_ i: Int) -> Binding<Bool> {
        Binding(get: { (alarm.weekdayMask & (1 << i)) != 0 }, set: { on in
            if on {
                alarm.weekdayMask = alarm.weekdayMask | (1 << i)
            } else {
                alarm.weekdayMask = alarm.weekdayMask & ~(1 << i)
            }
        })
    }
}

struct QuickRepliesView: View {
    @Environment(WatchSession.self) var session
    @State private var replies: [String] = []
    @State private var draft = ""
    @State private var dirty = false

    var body: some View {
        List {
            Section {
                ForEach(0..<replies.count, id: \.self) { i in
                    Text(replies[i])
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                replies.remove(at: i)
                                dirty = true
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                }
            } footer: {
                Text("Reply to calls and messages from your watch with one tap. Sending replies by SMS requires the SMS permission.")
            }
            Section("New Reply") {
                HStack {
                    TextField("Type a reply", text: $draft)
                    Button("Add") {
                        let text = draft.trimmingCharacters(in: .whitespaces)
                        if !text.isEmpty {
                            replies.append(text)
                            draft = ""
                            dirty = true
                        }
                    }
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || replies.count >= 10)
                }
            }
        }
        .navigationTitle("Quick Replies")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    session.saveQuickReplies(replies)
                    dirty = false
                }
                .disabled(!dirty || !session.isConnected)
            }
        }
        .onAppear {
            replies = session.quickReplies
        }
    }
}
