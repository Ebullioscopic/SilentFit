import Foundation
import Observation

/// User preferences and the last-known watch configuration.
public struct Preferences: Codable, Hashable {
    public var pairedWatch: DiscoveredWatch?
    /// Account id presented to the watch during binding. Reuse the NoiseFit id to take over
    /// an already-bound watch without resetting it (see docs/USER_GUIDE.md).
    public var accountId: String
    public var profile: UserProfile
    public var forwardNotifications: Bool
    public var forwardCalls: Bool
    public var forwardMessages: Bool
    public var mutedPackages: [String]
    public var deleteAfterSync: Bool
    public var autoReconnect: Bool
    public var demoMode: Bool
    public var onboarded: Bool
    public var heartRate: HeartRateSettings
    public var spo2: SpO2Settings
    public var stress: StressSettings
    public var doNotDisturb: ScheduleSettings
    public var raiseToWake: ScheduleSettings
    public var sedentary: ReminderSettings
    public var drinkWater: ReminderSettings
    public var alarms: [WatchAlarm]

    public init() {
        self.pairedWatch = nil
        self.accountId = Preferences.generateAccountId()
        self.profile = UserProfile()
        self.forwardNotifications = true
        self.forwardCalls = true
        self.forwardMessages = true
        self.mutedPackages = []
        self.deleteAfterSync = true
        self.autoReconnect = true
        self.demoMode = false
        self.onboarded = false
        self.heartRate = HeartRateSettings()
        self.spo2 = SpO2Settings()
        self.stress = StressSettings()
        self.doNotDisturb = ScheduleSettings(enabled: false, startMinutes: 22 * 60, endMinutes: 7 * 60)
        self.raiseToWake = ScheduleSettings(enabled: true, startMinutes: 0, endMinutes: 23 * 60 + 59)
        self.sedentary = ReminderSettings()
        self.drinkWater = ReminderSettings(enabled: false, startMinutes: 9 * 60, endMinutes: 21 * 60, intervalMinutes: 90, skipLunch: false)
        self.alarms = []
    }

    static func generateAccountId() -> String {
        var out = ""
        var i = 0
        while i < 18 {
            out = out + ByteUtils.hexDigits[Int.random(in: 0..<16)].lowercased()
            i += 1
        }
        return out
    }
}

@Observable public final class PreferencesStore {
    public var value: Preferences {
        didSet { save() }
    }
    @ObservationIgnored private let fileURL: URL

    public init(fileName: String = "preferences.json") {
        let url = URL.applicationSupportDirectory.appendingPathComponent(fileName)
        self.fileURL = url
        if let data = try? Data(contentsOf: url), let decoded = try? JSONDecoder().decode(Preferences.self, from: data) {
            self.value = decoded
        } else {
            self.value = Preferences()
        }
    }

    private func save() {
        do {
            try FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
            try JSONEncoder().encode(value).write(to: fileURL)
        } catch {
            logger.error("preferences save failed: \(error.localizedDescription)")
        }
    }
}
