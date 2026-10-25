import Foundation

// MARK: - Watch identity & status

public struct WatchInfo: Codable, Hashable {
    public var name: String
    public var address: String
    public var firmware: String
    public var model: String
    public var serial: String

    public init(name: String = "", address: String = "", firmware: String = "", model: String = "", serial: String = "") {
        self.name = name
        self.address = address
        self.firmware = firmware
        self.model = model
        self.serial = serial
    }
}

public enum ChargeState: Int, Codable {
    case unknown = 0
    case charging = 1
    case notCharging = 2
    case full = 3
}

public struct BatteryStatus: Codable, Hashable {
    public var level: Int
    public var state: ChargeState

    public init(level: Int, state: ChargeState) {
        self.level = level
        self.state = state
    }
}

public struct WatchFace: Identifiable, Hashable {
    public var id: String
    public var name: String
    public var isCurrent: Bool
    public var canRemove: Bool

    public init(id: String, name: String, isCurrent: Bool, canRemove: Bool) {
        self.id = id
        self.name = name
        self.isCurrent = isCurrent
        self.canRemove = canRemove
    }
}

/// Real-time totals pushed by the watch (REPORT_BASIC_DATA).
public struct LiveData: Hashable {
    public var steps: Int
    public var calories: Int
    public var distanceMeters: Int
    public var heartRate: Int
    public var spo2: Int
    public var stress: Int

    public init(steps: Int = 0, calories: Int = 0, distanceMeters: Int = 0, heartRate: Int = 0, spo2: Int = 0, stress: Int = 0) {
        self.steps = steps
        self.calories = calories
        self.distanceMeters = distanceMeters
        self.heartRate = heartRate
        self.spo2 = spo2
        self.stress = stress
    }
}

// MARK: - Media

public enum PlaybackState: Int, Codable {
    case none = 0
    case noPermission = 1
    case playing = 2
    case paused = 3
    case stopped = 4
    case nothingPlaying = 5
}

public struct NowPlaying: Hashable {
    public var state: PlaybackState
    public var title: String
    public var artist: String
    public var player: String
    public var volume: Int
    public var maxVolume: Int
    public var durationSeconds: Int
    public var positionSeconds: Int

    public init(state: PlaybackState = .nothingPlaying, title: String = "", artist: String = "", player: String = "", volume: Int = 0, maxVolume: Int = 16, durationSeconds: Int = 0, positionSeconds: Int = 0) {
        self.state = state
        self.title = title
        self.artist = artist
        self.player = player
        self.volume = volume
        self.maxVolume = maxVolume
        self.durationSeconds = durationSeconds
        self.positionSeconds = positionSeconds
    }
}

/// SEPlayerControlCommand values sent by the watch.
public enum MediaCommand: Int {
    case play = 1
    case pause = 2
    case previous = 3
    case next = 4
    case volumeUp = 5
    case volumeDown = 6
}

// MARK: - Settings mirrored to the watch

public enum Sex: Int, Codable, CaseIterable {
    case unspecified = 0
    case male = 1
    case female = 2
}

public struct UserProfile: Codable, Hashable {
    public var name: String
    public var heightCm: Int
    public var weightKg: Double
    public var age: Int
    public var sex: Sex
    public var stepGoal: Int
    public var calorieGoal: Int
    public var distanceGoalMeters: Int
    public var sleepGoalHours: Int
    public var metric: Bool

    public init(name: String = "", heightCm: Int = 170, weightKg: Double = 65.0, age: Int = 30, sex: Sex = .unspecified, stepGoal: Int = 8000, calorieGoal: Int = 400, distanceGoalMeters: Int = 5000, sleepGoalHours: Int = 8, metric: Bool = true) {
        self.name = name
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.age = age
        self.sex = sex
        self.stepGoal = stepGoal
        self.calorieGoal = calorieGoal
        self.distanceGoalMeters = distanceGoalMeters
        self.sleepGoalHours = sleepGoalHours
        self.metric = metric
    }
}

public struct HeartRateSettings: Codable, Hashable {
    public var enabled: Bool
    public var intervalMinutes: Int
    public var highAlertEnabled: Bool
    public var highAlertBpm: Int

    public init(enabled: Bool = true, intervalMinutes: Int = 5, highAlertEnabled: Bool = false, highAlertBpm: Int = 120) {
        self.enabled = enabled
        self.intervalMinutes = intervalMinutes
        self.highAlertEnabled = highAlertEnabled
        self.highAlertBpm = highAlertBpm
    }
}

public struct SpO2Settings: Codable, Hashable {
    public var enabled: Bool
    public var intervalMinutes: Int

    public init(enabled: Bool = true, intervalMinutes: Int = 10) {
        self.enabled = enabled
        self.intervalMinutes = intervalMinutes
    }
}

public struct StressSettings: Codable, Hashable {
    public var enabled: Bool
    public var relaxReminder: Bool

    public init(enabled: Bool = true, relaxReminder: Bool = false) {
        self.enabled = enabled
        self.relaxReminder = relaxReminder
    }
}

/// An on/off feature with a daily time window (minutes since midnight).
public struct ScheduleSettings: Codable, Hashable {
    public var enabled: Bool
    public var startMinutes: Int
    public var endMinutes: Int

    public init(enabled: Bool, startMinutes: Int, endMinutes: Int) {
        self.enabled = enabled
        self.startMinutes = startMinutes
        self.endMinutes = endMinutes
    }
}

public struct ReminderSettings: Codable, Hashable {
    public var enabled: Bool
    public var startMinutes: Int
    public var endMinutes: Int
    public var intervalMinutes: Int
    public var skipLunch: Bool

    public init(enabled: Bool = false, startMinutes: Int = 9 * 60, endMinutes: Int = 21 * 60, intervalMinutes: Int = 60, skipLunch: Bool = true) {
        self.enabled = enabled
        self.startMinutes = startMinutes
        self.endMinutes = endMinutes
        self.intervalMinutes = intervalMinutes
        self.skipLunch = skipLunch
    }
}

public struct WatchAlarm: Codable, Hashable, Identifiable {
    public var id: String
    public var minutes: Int
    /// Bit 0 = Monday … bit 6 = Sunday; 0 = once.
    public var weekdayMask: Int
    public var enabled: Bool
    public var label: String

    public init(id: String = UUID().uuidString, minutes: Int = 7 * 60, weekdayMask: Int = 0x1F, enabled: Bool = true, label: String = "Alarm") {
        self.id = id
        self.minutes = minutes
        self.weekdayMask = weekdayMask
        self.enabled = enabled
        self.label = label
    }
}

// MARK: - Health records

/// Identifier of one fitness record stored on the watch (SEBigFile_Fitness_TypeId).
public struct FitnessRecordID: Hashable {
    public let raw: [UInt8]
    public let year: Int
    public let month: Int
    public let day: Int
    public let functionType: Int
    public let length: Int

    public var dayKey: String {
        return HealthDay.key(year: year, month: month, day: day)
    }
}

public enum SleepStage: Int, Codable {
    case awake = 0
    case light = 1
    case deep = 2
    case rem = 3
}

public struct SleepSegment: Codable, Hashable {
    public var start: Int64
    public var minutes: Int
    public var stage: SleepStage

    public init(start: Int64, minutes: Int, stage: SleepStage) {
        self.start = start
        self.minutes = minutes
        self.stage = stage
    }
}

public struct SleepSummary: Codable, Hashable {
    public var start: Int64
    public var end: Int64
    public var totalMinutes: Int
    public var score: Int
    public var awakeMinutes: Int
    public var lightMinutes: Int
    public var deepMinutes: Int
    public var remMinutes: Int
    public var segments: [SleepSegment]

    public init(start: Int64 = 0, end: Int64 = 0, totalMinutes: Int = 0, score: Int = 0, awakeMinutes: Int = 0, lightMinutes: Int = 0, deepMinutes: Int = 0, remMinutes: Int = 0, segments: [SleepSegment] = []) {
        self.start = start
        self.end = end
        self.totalMinutes = totalMinutes
        self.score = score
        self.awakeMinutes = awakeMinutes
        self.lightMinutes = lightMinutes
        self.deepMinutes = deepMinutes
        self.remMinutes = remMinutes
        self.segments = segments
    }
}

/// A periodic sample series (heart rate, SpO₂, stress) for one day.
public struct SampleSeries: Codable, Hashable {
    /// Minutes between samples.
    public var interval: Int
    /// One value per interval starting at local midnight; 0 = no reading.
    public var values: [Int]
    public var min: Int
    public var max: Int

    public init(interval: Int = 0, values: [Int] = [], min: Int = 0, max: Int = 0) {
        self.interval = interval
        self.values = values
        self.min = min
        self.max = max
    }

    public var hasData: Bool {
        return values.contains { $0 > 0 }
    }

    public var average: Int {
        var sum = 0
        var count = 0
        for v in values where v > 0 {
            sum += v
            count += 1
        }
        return count == 0 ? 0 : sum / count
    }

    public var latest: Int {
        var i = values.count - 1
        while i >= 0 {
            if values[i] > 0 { return values[i] }
            i -= 1
        }
        return 0
    }
}

/// Everything SilentFit knows about one calendar day.
public struct HealthDay: Codable, Hashable, Identifiable {
    public var id: String
    public var steps: Int
    public var distanceMeters: Int
    public var calories: Int
    public var hourlySteps: [Int]
    public var hourlyDistance: [Int]
    public var hourlyCalories: [Int]
    public var standingHours: [Int]
    public var activeMinutesByHour: [Int]
    public var heartRate: SampleSeries
    public var spo2: SampleSeries
    public var stress: SampleSeries
    public var sleep: SleepSummary?

    public init(id: String) {
        self.id = id
        self.steps = 0
        self.distanceMeters = 0
        self.calories = 0
        self.hourlySteps = []
        self.hourlyDistance = []
        self.hourlyCalories = []
        self.standingHours = []
        self.activeMinutesByHour = []
        self.heartRate = SampleSeries()
        self.spo2 = SampleSeries()
        self.stress = SampleSeries()
        self.sleep = nil
    }

    public static func key(year: Int, month: Int, day: Int) -> String {
        let m = month < 10 ? "0\(month)" : "\(month)"
        let d = day < 10 ? "0\(day)" : "\(day)"
        return "\(year)-\(m)-\(d)"
    }

    public static func key(for date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return key(year: c.year ?? 2000, month: c.month ?? 1, day: c.day ?? 1)
    }

    public var date: Date {
        let parts = id.split(separator: "-")
        var c = DateComponents()
        if parts.count == 3 {
            c.year = Int(String(parts[0]))
            c.month = Int(String(parts[1]))
            c.day = Int(String(parts[2]))
        }
        return Calendar.current.date(from: c) ?? Date()
    }

    public var standingHourCount: Int {
        return standingHours.filter { $0 > 0 }.count
    }

    public var activeMinutes: Int {
        return activeMinutesByHour.reduce(0) { $0 + $1 }
    }
}
