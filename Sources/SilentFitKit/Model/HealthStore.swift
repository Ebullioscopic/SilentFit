import Foundation
import Observation

/// Persistent store of per-day health data synced from the watch.
@Observable public final class HealthStore {
    public private(set) var days: [String: HealthDay] = [:]
    @ObservationIgnored private let fileURL: URL

    public init(fileName: String = "health.json") {
        self.fileURL = URL.applicationSupportDirectory.appendingPathComponent(fileName)
        load()
    }

    public func day(_ key: String) -> HealthDay {
        return days[key] ?? HealthDay(id: key)
    }

    public var today: HealthDay {
        return day(HealthDay.key(for: Date()))
    }

    /// The last `count` calendar days ending today, oldest first (missing days are empty).
    public func recent(_ count: Int) -> [HealthDay] {
        var out: [HealthDay] = []
        let calendar = Calendar.current
        var i = count - 1
        while i >= 0 {
            if let date = calendar.date(byAdding: .day, value: -i, to: Date()) {
                out.append(day(HealthDay.key(for: date)))
            }
            i -= 1
        }
        return out
    }

    /// Most recent night of sleep recorded within the last week.
    public var lastSleep: HealthDay? {
        for d in recent(7).reversed() where d.sleep != nil {
            return d
        }
        return nil
    }

    public var hasAnyData: Bool {
        return !days.isEmpty
    }

    public func replace(with updated: [String: HealthDay]) {
        days = updated
        save()
    }

    public func clear() {
        days = [:]
        save()
    }

    private func load() {
        do {
            let data = try Data(contentsOf: fileURL)
            let list = try JSONDecoder().decode([HealthDay].self, from: data)
            var map: [String: HealthDay] = [:]
            for d in list {
                map[d.id] = d
            }
            days = map
        } catch {
            days = [:]
        }
    }

    private func save() {
        do {
            try FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
            let list = days.values.sorted { $0.id < $1.id }
            let data = try JSONEncoder().encode(list)
            try data.write(to: fileURL)
        } catch {
            logger.error("health save failed: \(error.localizedDescription)")
        }
    }
}
