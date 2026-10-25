import Foundation
import SwiftUI
import SilentFitKit

/// Semantic colors for each health metric, consistent across platforms.
enum Metric: String, CaseIterable, Identifiable, Hashable {
    case steps
    case calories
    case activeMinutes
    case distance
    case heartRate
    case sleep
    case spo2
    case stress
    case standing

    var id: String { rawValue }

    var title: String {
        switch self {
        case .steps: return "Steps"
        case .calories: return "Calories"
        case .activeMinutes: return "Active Minutes"
        case .distance: return "Distance"
        case .heartRate: return "Heart Rate"
        case .sleep: return "Sleep"
        case .spo2: return "Blood Oxygen"
        case .stress: return "Stress"
        case .standing: return "Stand Hours"
        }
    }

    var symbol: String {
        switch self {
        case .steps: return "figure.walk"
        case .calories: return "flame.fill"
        case .activeMinutes: return "figure.run"
        case .distance: return "point.topleft.down.curvedto.point.bottomright.up"
        case .heartRate: return "heart.fill"
        case .sleep: return "bed.double.fill"
        case .spo2: return "lungs.fill"
        case .stress: return "brain.head.profile"
        case .standing: return "figure.stand"
        }
    }

    var color: Color {
        switch self {
        case .steps: return Color.green
        case .calories: return Color.orange
        case .activeMinutes: return Color.mint
        case .distance: return Color.blue
        case .heartRate: return Color.red
        case .sleep: return Color.indigo
        case .spo2: return Color.cyan
        case .stress: return Color.purple
        case .standing: return Color.teal
        }
    }

    var unit: String {
        switch self {
        case .steps: return "steps"
        case .calories: return "kcal"
        case .activeMinutes: return "min"
        case .distance: return "km"
        case .heartRate: return "BPM"
        case .sleep: return ""
        case .spo2: return "%"
        case .stress: return ""
        case .standing: return "hr"
        }
    }

    /// Headline value of this metric for one day (0 when unknown).
    func value(for day: HealthDay) -> Double {
        switch self {
        case .steps: return Double(day.steps)
        case .calories: return Double(day.calories)
        case .activeMinutes: return Double(day.activeMinutes)
        case .distance: return Double(day.distanceMeters) / 1000.0
        case .heartRate: return Double(day.heartRate.average)
        case .sleep: return Double(day.sleep?.totalMinutes ?? 0)
        case .spo2: return Double(day.spo2.average)
        case .stress: return Double(day.stress.average)
        case .standing: return Double(day.standingHourCount)
        }
    }

    func formatted(_ value: Double) -> String {
        if value <= 0 { return "--" }
        switch self {
        case .distance: return Format.decimal(value, places: 2)
        case .sleep: return Format.duration(minutes: Int(value))
        default: return Format.grouped(Int(value))
        }
    }

    static let trends: [Metric] = [.steps, .calories, .distance, .activeMinutes, .heartRate, .sleep, .spo2, .stress, .standing]
}

enum Format {
    static func grouped(_ value: Int) -> String {
        var remaining = abs(value)
        var out = ""
        repeat {
            let chunk = remaining % 1000
            remaining = remaining / 1000
            if remaining > 0 {
                let padded = chunk < 10 ? "00\(chunk)" : (chunk < 100 ? "0\(chunk)" : "\(chunk)")
                out = "," + padded + out
            } else {
                out = "\(chunk)" + out
            }
        } while remaining > 0
        return value < 0 ? "-" + out : out
    }

    static func decimal(_ value: Double, places: Int) -> String {
        var factor = 1.0
        var i = 0
        while i < places {
            factor *= 10.0
            i += 1
        }
        let scaled = Int((value * factor).rounded())
        let whole = scaled / Int(factor)
        var fraction = String(abs(scaled % Int(factor)))
        while fraction.count < places {
            fraction = "0" + fraction
        }
        return places == 0 ? "\(whole)" : "\(whole).\(fraction)"
    }

    static func duration(minutes: Int) -> String {
        if minutes <= 0 { return "--" }
        let h = minutes / 60
        let m = minutes % 60
        if h == 0 { return "\(m)m" }
        return m == 0 ? "\(h)h" : "\(h)h \(m)m"
    }

    static func clock(minutes: Int) -> String {
        let h = (minutes / 60) % 24
        let m = minutes % 60
        return (h < 10 ? "0" : "") + "\(h):" + (m < 10 ? "0" : "") + "\(m)"
    }

    static func time(_ epochSeconds: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochSeconds))
        return date.formatted(date: .omitted, time: .shortened)
    }

    static func dayTitle(_ day: HealthDay) -> String {
        let key = day.id
        if key == HealthDay.key(for: Date()) { return "Today" }
        if let y = Calendar.current.date(byAdding: .day, value: -1, to: Date()), key == HealthDay.key(for: y) { return "Yesterday" }
        return day.date.formatted(date: .abbreviated, time: .omitted)
    }

    static func weekday(_ day: HealthDay) -> String {
        let symbols = ["S", "M", "T", "W", "T", "F", "S"]
        let index = Calendar.current.component(.weekday, from: day.date) - 1
        return symbols[max(0, min(6, index))]
    }

    static func relative(_ date: Date?) -> String {
        guard let date = date else { return "Never" }
        let seconds = Int(Date().timeIntervalSince(date))
        if seconds < 60 { return "Just now" }
        if seconds < 3600 { return "\(seconds / 60) min ago" }
        if seconds < 86400 { return "\(seconds / 3600) hr ago" }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}

/// Rounded grouped container used for dashboard content.
struct Card<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.secondarySystemGroupedBackgroundCompat))
    }
}

extension View {
    /// Groups children into one accessibility element (VoiceOver); Android reads children individually.
    public func accessibilityGroup(ignoringChildren: Bool = false) -> some View {
        #if os(Android)
        return self
        #else
        return self.accessibilityElement(children: ignoringChildren ? .ignore : .combine)
        #endif
    }

    /// Tabular digits so live numbers don't jitter.
    public func digits() -> some View {
        #if os(Android)
        return self
        #else
        return self.monospacedDigit()
        #endif
    }
}

extension Color {
    static var secondarySystemGroupedBackgroundCompat: Color {
        #if os(iOS)
        return Color(uiColor: .secondarySystemGroupedBackground)
        #elseif os(macOS)
        return Color(nsColor: .controlBackgroundColor)
        #else
        return Color.gray.opacity(0.14)
        #endif
    }

    static var groupedBackgroundCompat: Color {
        #if os(iOS)
        return Color(uiColor: .systemGroupedBackground)
        #elseif os(macOS)
        return Color(nsColor: .windowBackgroundColor)
        #else
        return Color.gray.opacity(0.06)
        #endif
    }
}

/// Small rounded-square icon badge, as used in Settings rows.
struct IconBadge: View {
    let symbol: String
    let color: Color
    var size: Double = 28

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.55, weight: .semibold))
            .foregroundStyle(Color.white)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.24).fill(color))
            .accessibilityHidden(true)
    }
}

/// A single progress ring.
struct Ring: View {
    let progress: Double
    let color: Color
    var lineWidth: Double = 14

    var body: some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.18), lineWidth: lineWidth)
            Circle()
                .trim(from: 0.0, to: max(0.001, min(progress, 1.0)))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(Angle.degrees(-90.0))
        }
    }
}

/// Three concentric rings: steps, calories, active minutes.
struct ActivityRings: View {
    let steps: Double
    let calories: Double
    let active: Double
    var size: Double = 132

    var body: some View {
        ZStack {
            Ring(progress: steps, color: Metric.steps.color, lineWidth: 14)
                .frame(width: size, height: size)
            Ring(progress: calories, color: Metric.calories.color, lineWidth: 14)
                .frame(width: size - 34, height: size - 34)
            Ring(progress: active, color: Metric.activeMinutes.color, lineWidth: 14)
                .frame(width: size - 68, height: size - 68)
        }
        .frame(width: size, height: size)
        .accessibilityGroup(ignoringChildren: true)
        .accessibilityLabel("Activity rings")
        .accessibilityValue("Steps \(Int(steps * 100)) percent, calories \(Int(calories * 100)) percent, active minutes \(Int(active * 100)) percent")
    }
}

/// Vertical bar chart drawn with shapes (no Charts dependency, works on Android).
struct BarChart: View {
    let values: [Double]
    let color: Color
    var height: Double = 120
    var labels: [String] = []
    var highlightLast = false
    var hourAxis = false

    var body: some View {
        let peak = max(values.max() ?? 0.0, 1.0)
        VStack(spacing: 6) {
            HStack(alignment: .bottom, spacing: 3) {
                ForEach(0..<values.count, id: \.self) { i in
                    let v = values[i]
                    RoundedRectangle(cornerRadius: 3)
                        .fill(v > 0 ? color.opacity(highlightLast && i != values.count - 1 ? 0.55 : 1.0) : color.opacity(0.12))
                        .frame(height: max(3.0, height * v / peak))
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: height, alignment: .bottom)
            if hourAxis {
                HourAxis()
            } else if !labels.isEmpty {
                HStack(spacing: 3) {
                    ForEach(0..<labels.count, id: \.self) { i in
                        Text(labels[i])
                            .font(.caption2)
                            .foregroundStyle(Color.secondary)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .accessibilityGroup(ignoringChildren: true)
        .accessibilityLabel("Chart")
    }
}

/// "12 AM · 6 AM · 12 PM · 6 PM" axis under 24-hour charts.
struct HourAxis: View {
    var body: some View {
        HStack(spacing: 0) {
            Text("12 AM").frame(maxWidth: .infinity, alignment: .leading)
            Text("6 AM").frame(maxWidth: .infinity, alignment: .leading)
            Text("12 PM").frame(maxWidth: .infinity, alignment: .leading)
            Text("6 PM").frame(maxWidth: .infinity, alignment: .leading)
        }
        .font(.caption2)
        .foregroundStyle(Color.secondary)
        .lineLimit(1)
    }
}

/// Range chart: each column shows a sample value as a dot-topped bar, useful for HR/SpO₂/stress.
struct SampleChart: View {
    let series: SampleSeries
    let color: Color
    var height: Double = 140
    var buckets = 48

    var body: some View {
        let columns = bucketed()
        let lo = max(0.0, Double(series.min) - 5.0)
        let hi = max(lo + 1.0, Double(series.max) + 5.0)
        VStack(spacing: 6) {
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(0..<columns.count, id: \.self) { i in
                    let v = columns[i]
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        if v > 0 {
                            Capsule()
                                .fill(color)
                                .frame(height: max(4.0, height * (v - lo) / (hi - lo)))
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: height)
            HourAxis()
        }
        .accessibilityGroup(ignoringChildren: true)
        .accessibilityLabel("Readings throughout the day")
    }

    private func bucketed() -> [Double] {
        let values = series.values
        if values.isEmpty { return [] }
        let per = max(1, values.count / buckets)
        var out: [Double] = []
        var i = 0
        while i < values.count {
            var sum = 0
            var n = 0
            var j = i
            while j < min(values.count, i + per) {
                if values[j] > 0 {
                    sum += values[j]
                    n += 1
                }
                j += 1
            }
            out.append(n == 0 ? 0.0 : Double(sum) / Double(n))
            i += per
        }
        return out
    }
}

/// Connection status chip with a colored dot.
struct StatusPill: View {
    let state: LinkState
    let text: String

    var color: Color {
        switch state {
        case .connected: return Color.green
        case .connecting, .pairing: return Color.orange
        case .failed: return Color.red
        case .disconnected: return Color.gray
        }
    }

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(text)
                .font(.footnote)
                .foregroundStyle(Color.secondary)
                .lineLimit(2)
        }
        .accessibilityGroup()
    }
}

struct BatteryLabel: View {
    let battery: BatteryStatus?

    var symbol: String {
        guard let b = battery else { return "battery.0" }
        if b.state == .charging { return "battery.100.bolt" }
        if b.level >= 88 { return "battery.100" }
        if b.level >= 63 { return "battery.75" }
        if b.level >= 38 { return "battery.50" }
        if b.level >= 13 { return "battery.25" }
        return "battery.0"
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: symbol)
                .foregroundStyle((battery?.level ?? 100) <= 15 ? Color.red : Color.green)
            Text(battery != nil ? "\(battery!.level)%" : "--")
                .font(.subheadline)
                .digits()
        }
        .accessibilityGroup(ignoringChildren: true)
        .accessibilityLabel("Battery \(battery?.level ?? 0) percent")
    }
}

/// Empty-state placeholder: the system `ContentUnavailableView` on Apple platforms, an equivalent layout on Android.
struct EmptyStateView: View {
    let title: String
    let symbol: String
    let message: String
    var actionTitle: String = ""
    var action: (() -> Void)? = nil

    var body: some View {
        #if os(Android)
        VStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 48))
                .foregroundStyle(Color.secondary)
            Text(title)
                .font(.title2.weight(.bold))
                .multilineTextAlignment(.center)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Color.secondary)
                .multilineTextAlignment(.center)
            if !actionTitle.isEmpty {
                Button(actionTitle) {
                    action?()
                }
                .buttonStyle(.borderedProminent)
                .padding(.top, 8)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity)
        #else
        ContentUnavailableView {
            Label(title, systemImage: symbol)
        } description: {
            Text(message)
        } actions: {
            if !actionTitle.isEmpty {
                Button(actionTitle) {
                    action?()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        #endif
    }
}

/// Stepper clamped to a range (SkipUI does not implement the `in:` initializers).
struct BoundedStepper: View {
    let title: String
    @Binding var value: Int
    let lower: Int
    let upper: Int
    var step: Int = 1

    var body: some View {
        Stepper(title, onIncrement: {
            value = min(upper, value + step)
        }, onDecrement: {
            value = max(lower, value - step)
        })
    }
}

struct BoundedDoubleStepper: View {
    let title: String
    @Binding var value: Double
    let lower: Double
    let upper: Double
    var step: Double = 1.0

    var body: some View {
        Stepper(title, onIncrement: {
            value = min(upper, value + step)
        }, onDecrement: {
            value = max(lower, value - step)
        })
    }
}

/// Watch glyph inside a soft circle, used as the device "hero" image.
struct WatchHero: View {
    let connected: Bool
    var size: Double = 64

    var body: some View {
        Image(systemName: "applewatch")
            .font(.system(size: size * 0.5, weight: .regular))
            .foregroundStyle(connected ? Color.accentColor : Color.secondary)
            .frame(width: size, height: size)
            .background(Circle().fill((connected ? Color.accentColor : Color.gray).opacity(0.15)))
            .accessibilityHidden(true)
    }
}
