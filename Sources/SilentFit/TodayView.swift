import SwiftUI
import SilentFitKit

struct TodayView: View {
    @Environment(WatchSession.self) var session
    @Environment(PreferencesStore.self) var preferences
    @Binding var selectedTab: AppTab

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    connectionCard
                    activityCard
                    tiles
                    hourlyStepsCard
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .background(Color.groupedBackgroundCompat)
            .navigationTitle("Today")
            .refreshable {
                session.syncHealth()
            }
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        session.syncHealth()
                    } label: {
                        Label("Sync", systemImage: "arrow.triangle.2.circlepath")
                    }
                    .disabled(!session.isConnected || session.isSyncing)
                }
            }
            .navigationDestination(for: Metric.self) { metric in
                MetricDetailView(metric: metric)
            }
        }
    }

    private var today: HealthDay {
        // The watch finalizes today's record later; prefer its live totals when they are ahead.
        var day = session.health.today
        if let live = session.live {
            if live.steps > day.steps { day.steps = live.steps }
            if live.calories > day.calories { day.calories = live.calories }
            if live.distanceMeters > day.distanceMeters { day.distanceMeters = live.distanceMeters }
        }
        return day
    }

    // MARK: Connection

    @ViewBuilder private var connectionCard: some View {
        Card {
            if preferences.value.pairedWatch == nil {
                HStack(spacing: 14) {
                    WatchHero(connected: false, size: 52)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Pair Your Watch")
                            .font(.headline)
                        Text("Connect your Noise watch to see activity, heart rate and sleep.")
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                    }
                }
                Button {
                    selectedTab = .watch
                } label: {
                    Text("Set Up Watch")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            } else {
                Button {
                    selectedTab = .watch
                } label: {
                    HStack(spacing: 14) {
                        WatchHero(connected: session.isConnected, size: 52)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(session.displayName)
                                .font(.headline)
                                .foregroundStyle(Color.primary)
                            StatusPill(state: session.state, text: session.statusText)
                        }
                        Spacer()
                        if session.battery != nil {
                            BatteryLabel(battery: session.battery)
                        }
                    }
                }
                .buttonStyle(.plain)
                if session.isSyncing {
                    VStack(alignment: .leading, spacing: 6) {
                        ProgressView(value: session.syncProgress)
                        Text(session.syncMessage)
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                    }
                } else {
                    Text("Last synced \(Format.relative(session.lastSync).lowercased())")
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
            }
        }
    }

    // MARK: Activity

    private var activityCard: some View {
        let profile = preferences.value.profile
        let stepGoal = Double(max(profile.stepGoal, 1))
        let calorieGoal = Double(max(profile.calorieGoal, 1))
        let activeGoal = 30.0
        return Card {
            Text("Activity")
                .font(.title3.weight(.semibold))
            HStack(spacing: 20) {
                ActivityRings(steps: Double(today.steps) / stepGoal, calories: Double(today.calories) / calorieGoal, active: Double(today.activeMinutes) / activeGoal, size: 128)
                VStack(alignment: .leading, spacing: 12) {
                    ringLegend(.steps, value: today.steps, goal: profile.stepGoal)
                    ringLegend(.calories, value: today.calories, goal: profile.calorieGoal)
                    ringLegend(.activeMinutes, value: today.activeMinutes, goal: Int(activeGoal))
                }
                Spacer(minLength: 0)
            }
        }
    }

    private func ringLegend(_ metric: Metric, value: Int, goal: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(metric.title)
                .font(.caption)
                .foregroundStyle(Color.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(Format.grouped(value))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(metric.color)
                    .digits()
                Text("/ \(Format.grouped(goal)) \(metric.unit)")
                    .font(.caption)
                    .foregroundStyle(Color.secondary)
            }
        }
        .accessibilityGroup()
    }

    // MARK: Tiles

    private var tiles: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                tile(.heartRate, value: heartValue, detail: heartDetail)
                tile(.sleep, value: sleepValue, detail: sleepDetail)
            }
            HStack(spacing: 12) {
                tile(.spo2, value: today.spo2.hasData ? "\(today.spo2.average)%" : "--", detail: today.spo2.hasData ? "Range \(today.spo2.min)–\(today.spo2.max)%" : "No readings yet")
                tile(.stress, value: today.stress.hasData ? "\(today.stress.average)" : "--", detail: today.stress.hasData ? stressLevel(today.stress.average) : "No readings yet")
            }
            HStack(spacing: 12) {
                tile(.distance, value: today.distanceMeters > 0 ? "\(Format.decimal(Double(today.distanceMeters) / 1000.0, places: 2)) km" : "--", detail: "\(Format.grouped(today.calories)) kcal burned")
                tile(.standing, value: "\(today.standingHourCount) hr", detail: "Hours with standing")
            }
        }
    }

    private var heartValue: String {
        if let live = session.live, live.heartRate > 0 {
            return "\(live.heartRate) BPM"
        }
        return today.heartRate.hasData ? "\(today.heartRate.latest) BPM" : "--"
    }

    private var heartDetail: String {
        return today.heartRate.hasData ? "Range \(today.heartRate.min)–\(today.heartRate.max) BPM" : "No readings yet"
    }

    private var sleepValue: String {
        guard let night = session.health.lastSleep?.sleep else { return "--" }
        return Format.duration(minutes: night.totalMinutes)
    }

    private var sleepDetail: String {
        guard let night = session.health.lastSleep?.sleep else { return "No sleep recorded" }
        return "Score \(night.score) · Deep \(Format.duration(minutes: night.deepMinutes))"
    }

    private func stressLevel(_ value: Int) -> String {
        if value < 30 { return "Relaxed" }
        if value < 60 { return "Normal" }
        if value < 80 { return "Medium" }
        return "High"
    }

    private func tile(_ metric: Metric, value: String, detail: String) -> some View {
        NavigationLink(value: metric) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    Image(systemName: metric.symbol)
                        .foregroundStyle(metric.color)
                    Text(metric.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(metric.color)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.secondary)
                }
                Text(value)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Color.secondary)
                    .lineLimit(1)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.secondarySystemGroupedBackgroundCompat))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(metric.title), \(value), \(detail)")
    }

    // MARK: Hourly steps

    private var hourlyStepsCard: some View {
        Card {
            HStack {
                Label("Steps by Hour", systemImage: "figure.walk")
                    .font(.headline)
                    .foregroundStyle(Metric.steps.color)
                Spacer()
                Text("\(Format.grouped(today.steps)) total")
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
            }
            if today.hourlySteps.isEmpty {
                Text("Sync your watch to see today's steps.")
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
                    .frame(maxWidth: .infinity, minHeight: 80)
            } else {
                BarChart(values: today.hourlySteps.map { Double($0) }, color: Metric.steps.color, height: 110, hourAxis: true)
            }
        }
    }
}
