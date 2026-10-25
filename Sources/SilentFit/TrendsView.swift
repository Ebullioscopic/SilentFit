import SwiftUI
import SilentFitKit

struct TrendsView: View {
    @Environment(WatchSession.self) var session
    @State private var range = 7

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Range", selection: $range) {
                        Text("Week").tag(7)
                        Text("Month").tag(30)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                }
                if !session.health.hasAnyData {
                    Section {
                        EmptyStateView(title: "No Health Data Yet", symbol: "chart.bar.xaxis", message: "Connect and sync your watch to build your history.")
                    }
                }
                ForEach(Metric.trends) { metric in
                    Section {
                        NavigationLink(value: metric) {
                            TrendRow(metric: metric, days: session.health.recent(range))
                        }
                    }
                }
            }
            .navigationTitle("Trends")
            .navigationDestination(for: Metric.self) { metric in
                MetricDetailView(metric: metric)
            }
        }
    }
}

struct TrendRow: View {
    let metric: Metric
    let days: [HealthDay]

    var body: some View {
        let values = days.map { metric.value(for: $0) }
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(metric.title, systemImage: metric.symbol)
                    .font(.headline)
                    .foregroundStyle(metric.color)
                Spacer()
            }
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(metric.formatted(average(values)))
                    .font(.title2.weight(.semibold))
                    .digits()
                if !metric.unit.isEmpty {
                    Text(metric.unit)
                        .font(.subheadline)
                        .foregroundStyle(Color.secondary)
                }
                Spacer()
                Text("Daily average")
                    .font(.caption)
                    .foregroundStyle(Color.secondary)
            }
            BarChart(values: values, color: metric.color, height: 56, highlightLast: true)
        }
        .padding(.vertical, 6)
    }

    private func average(_ values: [Double]) -> Double {
        var sum = 0.0
        var n = 0
        for v in values where v > 0 {
            sum += v
            n += 1
        }
        return n == 0 ? 0.0 : sum / Double(n)
    }
}

struct MetricDetailView: View {
    let metric: Metric
    @Environment(WatchSession.self) var session
    @State private var offset = 0

    private var day: HealthDay {
        let date = Calendar.current.date(byAdding: .day, value: -offset, to: Date()) ?? Date()
        return session.health.day(HealthDay.key(for: date))
    }

    var body: some View {
        List {
            Section {
                dayHeader
                headline
                chart
            }
            if metric == .sleep, let sleep = day.sleep {
                SleepBreakdown(sleep: sleep)
            }
            statsSection
            Section("Last 7 Days") {
                let week = weekEnding(offset)
                BarChart(values: week.map { metric.value(for: $0) }, color: metric.color, height: 100, labels: week.map { Format.weekday($0) }, highlightLast: true)
                    .padding(.vertical, 8)
            }
            Section {
                Text(about)
                    .font(.footnote)
                    .foregroundStyle(Color.secondary)
            } header: {
                Text("About \(metric.title)")
            }
        }
        .navigationTitle(metric.title)
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
    }

    private func weekEnding(_ daysAgo: Int) -> [HealthDay] {
        var out: [HealthDay] = []
        var i = daysAgo + 6
        while i >= daysAgo {
            if let date = Calendar.current.date(byAdding: .day, value: -i, to: Date()) {
                out.append(session.health.day(HealthDay.key(for: date)))
            }
            i -= 1
        }
        return out
    }

    private var dayHeader: some View {
        HStack {
            Button {
                offset += 1
            } label: {
                Image(systemName: "chevron.right")
                    .rotationEffect(Angle.degrees(180.0))
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.borderless)
            .disabled(offset >= 29)
            .accessibilityLabel("Previous day")
            Spacer()
            Text(Format.dayTitle(day))
                .font(.headline)
            Spacer()
            Button {
                offset -= 1
            } label: {
                Image(systemName: "chevron.right")
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.borderless)
            .disabled(offset == 0)
            .accessibilityLabel("Next day")
        }
    }

    private var headline: some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Image(systemName: metric.symbol)
                .font(.title2)
                .foregroundStyle(metric.color)
            Text(metric.formatted(metric.value(for: day)))
                .font(.system(size: 40, weight: .bold))
                .digits()
            if !metric.unit.isEmpty {
                Text(metric == .heartRate || metric == .spo2 || metric == .stress ? "avg \(metric.unit)" : metric.unit)
                    .font(.headline)
                    .foregroundStyle(Color.secondary)
            }
        }
        .accessibilityGroup()
    }

    @ViewBuilder private var chart: some View {
        switch metric {
        case .steps:
            hourly(day.hourlySteps)
        case .calories:
            hourly(day.hourlyCalories)
        case .distance:
            hourly(day.hourlyDistance)
        case .activeMinutes:
            hourly(day.activeMinutesByHour)
        case .standing:
            hourly(day.standingHours)
        case .heartRate:
            samples(day.heartRate)
        case .spo2:
            samples(day.spo2)
        case .stress:
            samples(day.stress)
        case .sleep:
            if let sleep = day.sleep {
                SleepTimeline(sleep: sleep)
                    .padding(.vertical, 8)
            } else {
                emptyChart
            }
        }
    }

    @ViewBuilder private func hourly(_ values: [Int]) -> some View {
        if values.isEmpty || !values.contains(where: { $0 > 0 }) {
            emptyChart
        } else {
            BarChart(values: values.map { Double($0) }, color: metric.color, height: 140, hourAxis: true)
                .padding(.vertical, 8)
        }
    }

    @ViewBuilder private func samples(_ series: SampleSeries) -> some View {
        if series.hasData {
            SampleChart(series: series, color: metric.color)
                .padding(.vertical, 8)
        } else {
            emptyChart
        }
    }

    private var emptyChart: some View {
        Text("No data for this day")
            .font(.subheadline)
            .foregroundStyle(Color.secondary)
            .frame(maxWidth: .infinity, minHeight: 120)
    }

    @ViewBuilder private var statsSection: some View {
        switch metric {
        case .heartRate:
            seriesStats(day.heartRate, unit: "BPM")
        case .spo2:
            seriesStats(day.spo2, unit: "%")
        case .stress:
            seriesStats(day.stress, unit: "")
        case .steps, .distance, .calories:
            Section("Summary") {
                LabeledContent("Steps", value: Format.grouped(day.steps))
                LabeledContent("Distance", value: "\(Format.decimal(Double(day.distanceMeters) / 1000.0, places: 2)) km")
                LabeledContent("Calories", value: "\(Format.grouped(day.calories)) kcal")
                LabeledContent("Most active hour", value: peakHour(day.hourlySteps))
            }
        case .activeMinutes, .standing:
            Section("Summary") {
                LabeledContent("Active minutes", value: "\(day.activeMinutes) min")
                LabeledContent("Stand hours", value: "\(day.standingHourCount) of 24")
            }
        case .sleep:
            EmptyView()
        }
    }

    private func seriesStats(_ s: SampleSeries, unit: String) -> some View {
        Section("Summary") {
            LabeledContent("Average", value: s.hasData ? "\(s.average) \(unit)" : "--")
            LabeledContent("Lowest", value: s.hasData ? "\(s.min) \(unit)" : "--")
            LabeledContent("Highest", value: s.hasData ? "\(s.max) \(unit)" : "--")
            LabeledContent("Latest", value: s.hasData ? "\(s.latest) \(unit)" : "--")
        }
    }

    private func peakHour(_ hours: [Int]) -> String {
        var best = -1
        var bestValue = 0
        for i in 0..<hours.count where hours[i] > bestValue {
            best = i
            bestValue = hours[i]
        }
        return best < 0 ? "--" : Format.clock(minutes: best * 60)
    }

    private var about: String {
        switch metric {
        case .steps: return "Steps are counted by your watch's motion sensor and synced each time SilentFit connects."
        case .calories: return "Active calories estimated by your watch from movement and your profile."
        case .distance: return "Distance is estimated from your steps and height. Keep your profile up to date for best accuracy."
        case .activeMinutes: return "Minutes of brisk activity detected by the watch."
        case .standing: return "Hours in which you stood and moved for at least a minute."
        case .heartRate: return "Measured periodically by the optical sensor. Change the interval in Watch › Heart Rate."
        case .sleep: return "Sleep stages are estimated by your watch from movement and heart rate."
        case .spo2: return "Blood oxygen saturation measured periodically by your watch. Not intended for medical use."
        case .stress: return "Stress is estimated from heart rate variability on a 1–100 scale."
        }
    }
}

struct SleepTimeline: View {
    let sleep: SleepSummary

    static func color(_ stage: SleepStage) -> Color {
        switch stage {
        case .awake: return Color.orange
        case .rem: return Color.cyan
        case .light: return Color.blue
        case .deep: return Color.indigo
        }
    }

    static func name(_ stage: SleepStage) -> String {
        switch stage {
        case .awake: return "Awake"
        case .rem: return "REM"
        case .light: return "Light"
        case .deep: return "Deep"
        }
    }

    var body: some View {
        let total = max(1, sleep.segments.reduce(0) { $0 + $1.minutes })
        VStack(alignment: .leading, spacing: 8) {
            GeometryReader { proxy in
                HStack(alignment: .bottom, spacing: 0) {
                    ForEach(0..<sleep.segments.count, id: \.self) { i in
                        let seg = sleep.segments[i]
                        Rectangle()
                            .fill(SleepTimeline.color(seg.stage))
                            .frame(width: proxy.size.width * Double(seg.minutes) / Double(total), height: barHeight(seg.stage))
                    }
                }
                .frame(height: 72, alignment: .bottom)
            }
            .frame(height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            HStack {
                Text(Format.time(sleep.start))
                Spacer()
                Text(Format.time(sleep.end))
            }
            .font(.caption)
            .foregroundStyle(Color.secondary)
        }
        .accessibilityGroup(ignoringChildren: true)
        .accessibilityLabel("Sleep from \(Format.time(sleep.start)) to \(Format.time(sleep.end))")
    }

    private func barHeight(_ stage: SleepStage) -> Double {
        switch stage {
        case .awake: return 72
        case .rem: return 54
        case .light: return 36
        case .deep: return 18
        }
    }
}

struct SleepBreakdown: View {
    let sleep: SleepSummary

    var body: some View {
        Section("Stages") {
            HStack(spacing: 16) {
                ZStack {
                    Ring(progress: Double(sleep.score) / 100.0, color: Color.indigo, lineWidth: 7)
                    Text("\(sleep.score)")
                        .font(.headline)
                        .digits()
                }
                .frame(width: 54, height: 54)
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Sleep Score \(sleep.score)")
                        .font(.headline)
                    Text(sleep.score >= 80 ? "Great night" : (sleep.score >= 60 ? "Fair night" : "Restless night"))
                        .font(.subheadline)
                        .foregroundStyle(Color.secondary)
                }
            }
            .padding(.vertical, 4)
            stageRow(.deep, sleep.deepMinutes)
            stageRow(.light, sleep.lightMinutes)
            stageRow(.rem, sleep.remMinutes)
            stageRow(.awake, sleep.awakeMinutes)
        }
    }

    private func stageRow(_ stage: SleepStage, _ minutes: Int) -> some View {
        let total = max(1, sleep.totalMinutes)
        return HStack {
            Circle()
                .fill(SleepTimeline.color(stage))
                .frame(width: 10, height: 10)
            Text(SleepTimeline.name(stage))
            Spacer()
            Text(Format.duration(minutes: minutes))
                .foregroundStyle(Color.secondary)
            Text("\(minutes * 100 / total)%")
                .font(.footnote)
                .foregroundStyle(Color.secondary)
                .frame(width: 44, alignment: .trailing)
        }
        .accessibilityGroup()
    }
}
