import Foundation

/// SA.SEWear.Type values.
public enum WearType {
    public static let account = 1
    public static let device = 2
    public static let systemTime = 3
    public static let userSettings = 4
    public static let sporting = 5
    public static let fitness = 6
    public static let microFunction = 8
    public static let notification = 9
    public static let music = 10
    public static let settingMenu = 11
    public static let factory = 12
    public static let watchFace = 14
    public static let weather = 15
}

/// SA.SEWear oneof payload field numbers.
enum WearField {
    static let type = 1
    static let id = 2
    static let device = 5
    static let systemTime = 6
    static let userSettings = 7
    static let microFunction = 11
    static let notification = 12
    static let music = 13
    static let settingMenu = 14
    static let watchFace = 17
    static let errorCode = 100
}

/// Command ids per Wear type (subset used by SilentFit).
public enum WearID {
    // DEVICE
    public static let getDeviceInfo = 0
    public static let getBattery = 1
    // SYSTEM_TIME
    public static let setSystemTime = 0
    // USER_SETTINGS
    public static let setUnitType = 2
    public static let setUserProfile = 4
    // MICRO_FUNCTION
    public static let wearFindPhone = 0
    public static let findWear = 1
    public static let phoneBasicDataReport = 4
    public static let reportBasicData = 5
    public static let phoneFindPhone = 10
    // NOTIFICATION
    public static let sendSystemNotification = 2
    public static let sendAppNotification = 3
    public static let sendCallState = 4
    public static let getQuickReplies = 5
    public static let setQuickReplies = 6
    public static let sendQuickReplyMessage = 7
    // MUSIC
    public static let requestMusicState = 0
    public static let sendMusicCommand = 1
    public static let quitMusic = 2
    public static let syncMusicInfo = 3
    // SETTING_MENU
    public static let getWristScreen = 0
    public static let setWristScreen = 1
    public static let getHeartRateMonitor = 6
    public static let setHeartRateMonitor = 7
    public static let getSedentary = 9
    public static let setSedentary = 10
    public static let getAlarms = 12
    public static let setAlarms = 13
    public static let getDrinkWater = 18
    public static let setDrinkWater = 19
    public static let getDoNotDisturb = 21
    public static let setDoNotDisturb = 22
    public static let getStressMode = 76
    public static let setStressMode = 77
    public static let getSpO2Settings = 82
    public static let setSpO2Settings = 83
    public static let appControlFileSendStatus = 136
    public static let deviceControlFileSendStatus = 137
    public static let getFitnessIdList = 138
    public static let deleteFitnessIdList = 139
    // NEW_WATCH_FACE
    public static let getWatchFaces = 0
    public static let setWatchFace = 1
    public static let removeWatchFace = 2
    public static let watchChangedFace = 13
}

/// Private (non-protobuf) command ids exchanged during connection.
public enum BerryPrivateID {
    public static let hello = 1
    public static let userId = 2
    public static let bindRequest = 3
    public static let bindConfirm = 4
}

/// Large-file categories (SEBigFileFitnessGetIdList.Type / frame type byte).
public enum BerryFileType {
    public static let sport = 7
    public static let daily = 15
}

/// A fully described command ready to be framed with a serial number.
public struct BerryCommand {
    public let event: Int
    public let type: Int
    public let payload: [UInt8]
    public let label: String

    public static func wear(_ wearType: Int, _ id: Int, field: Int = 0, body: ProtoWriter? = nil, label: String) -> BerryCommand {
        let w = ProtoWriter().uint(WearField.type, wearType).uint(WearField.id, id)
        if let body = body {
            w.message(field, body)
        }
        return BerryCommand(event: BerryFrame.eventProtobuf, type: BerryFrame.typeProtobuf, payload: w.bytes, label: label)
    }

    public static func privateCommand(_ cmdId: Int, data: [UInt8] = [], label: String) -> BerryCommand {
        return BerryCommand(event: BerryFrame.eventPrivate, type: BerryFrame.typePrivate, payload: BerryFrame.privatePayload(cmdId: cmdId, data: data), label: label)
    }
}

/// Builders for every command SilentFit sends.
public enum BerryCommands {
    // MARK: Connection

    public static func hello() -> BerryCommand {
        return BerryCommand.privateCommand(BerryPrivateID.hello, label: "hello")
    }

    /// `[len][userId] 0xBB [len][phoneName] [len][systemVersion]`
    public static func userId(_ userId: String, phoneName: String, systemVersion: String) -> BerryCommand {
        let uid = ByteUtils.utf8(String(userId.prefix(100)))
        let name = ByteUtils.utf8(String(phoneName.prefix(60)))
        let version = ByteUtils.utf8(String(systemVersion.prefix(50)))
        var data: [UInt8] = [UInt8(uid.count & 0xFF)]
        data.append(contentsOf: uid)
        data.append(UInt8(0xBB))
        data.append(UInt8(name.count & 0xFF))
        data.append(contentsOf: name)
        data.append(UInt8(version.count & 0xFF))
        data.append(contentsOf: version)
        return BerryCommand.privateCommand(BerryPrivateID.userId, data: data, label: "userId")
    }

    public static func bindRequest() -> BerryCommand {
        return BerryCommand.privateCommand(BerryPrivateID.bindRequest, label: "bindRequest")
    }

    public static func bindConfirm(_ success: Bool) -> BerryCommand {
        return BerryCommand.privateCommand(BerryPrivateID.bindConfirm, data: [UInt8(success ? 1 : 0)], label: "bindConfirm")
    }

    // MARK: Device

    public static func deviceInfo() -> BerryCommand {
        return BerryCommand.wear(WearType.device, WearID.getDeviceInfo, label: "deviceInfo")
    }

    public static func battery() -> BerryCommand {
        return BerryCommand.wear(WearType.device, WearID.getBattery, label: "battery")
    }

    /// Unix time (UTC seconds) plus timezone offset in quarter hours.
    public static func setTime(_ date: Date, timeZone: TimeZone) -> BerryCommand {
        let seconds = Int64(date.timeIntervalSince1970)
        let quarters = timeZone.secondsFromGMT(for: date) / 900
        let timeSet = ProtoWriter().uint64(1, seconds).sint(2, quarters)
        let systemTime = ProtoWriter().message(1, timeSet)
        return BerryCommand.wear(WearType.systemTime, WearID.setSystemTime, field: WearField.systemTime, body: systemTime, label: "setTime")
    }

    // MARK: Micro functions

    public static func findWatch() -> BerryCommand {
        return BerryCommand.wear(WearType.microFunction, WearID.findWear, label: "findWatch")
    }

    public static func phoneFoundStop() -> BerryCommand {
        let body = ProtoWriter().uint(1, 1)
        return BerryCommand.wear(WearType.microFunction, WearID.phoneFindPhone, field: WearField.microFunction, body: body, label: "findPhoneStop")
    }

    public static func liveData(_ start: Bool) -> BerryCommand {
        let body = ProtoWriter().uint(3, start ? 0 : 1)
        return BerryCommand.wear(WearType.microFunction, WearID.phoneBasicDataReport, field: WearField.microFunction, body: body, label: "liveData")
    }

    // MARK: Notifications

    public static func appNotification(appName: String, packageName: String, title: String, text: String, key: String) -> BerryCommand {
        let n = ProtoWriter()
            .string(1, String(appName.prefix(40)))
            .string(2, packageName)
            .string(3, String(title.prefix(80)))
            .string(4, String(text.prefix(400)))
            .string(5, String(title.prefix(80)))
            .string(6, key)
        let body = ProtoWriter().message(2, n)
        return BerryCommand.wear(WearType.notification, WearID.sendAppNotification, field: WearField.notification, body: body, label: "appNotification")
    }

    /// `kind`: 0 incoming call, 1 missed call, 2 SMS.
    public static func systemNotification(kind: Int, number: String, contact: String, message: String, appName: String, packageName: String) -> BerryCommand {
        let n = ProtoWriter()
            .uint(1, kind)
            .string(2, number)
            .string(3, contact)
            .string(4, String(message.prefix(400)))
            .string(5, appName)
            .string(6, packageName)
        let body = ProtoWriter().message(1, n)
        return BerryCommand.wear(WearType.notification, WearID.sendSystemNotification, field: WearField.notification, body: body, label: "systemNotification")
    }

    /// `state`: 0 answered, 1 hung up, 2 muted.
    public static func callState(_ state: Int) -> BerryCommand {
        let body = ProtoWriter().uint(4, state)
        return BerryCommand.wear(WearType.notification, WearID.sendCallState, field: WearField.notification, body: body, label: "callState")
    }

    public static func quickReplies() -> BerryCommand {
        return BerryCommand.wear(WearType.notification, WearID.getQuickReplies, label: "quickReplies")
    }

    public static func setQuickReplies(_ replies: [String]) -> BerryCommand {
        let list = ProtoWriter()
        for reply in replies {
            list.message(1, ProtoWriter().string(1, String(reply.prefix(60))))
        }
        let body = ProtoWriter().message(5, list)
        return BerryCommand.wear(WearType.notification, WearID.setQuickReplies, field: WearField.notification, body: body, label: "setQuickReplies")
    }

    // MARK: Music

    public static func musicInfo(_ info: NowPlaying) -> BerryCommand {
        let p = ProtoWriter()
            .uint(1, info.state.rawValue)
            .string(2, String(info.title.prefix(60)))
        if !info.player.isEmpty { p.string(3, info.player) }
        if !info.artist.isEmpty { p.string(4, String(info.artist.prefix(60))) }
        p.uint(5, info.volume)
            .uint(6, info.maxVolume)
            .uint(7, info.durationSeconds)
            .uint(8, info.positionSeconds)
            .uint(10, 1)
        let body = ProtoWriter().message(1, p)
        return BerryCommand.wear(WearType.music, WearID.syncMusicInfo, field: WearField.music, body: body, label: "musicInfo")
    }

    // MARK: Health data transfer

    public static func fitnessIdList(fileType: Int) -> BerryCommand {
        let request = ProtoWriter().uint(1, fileType)
        let menu = ProtoWriter().message(58, request)
        return BerryCommand.wear(WearType.settingMenu, WearID.getFitnessIdList, field: WearField.settingMenu, body: menu, label: "fitnessIdList")
    }

    public static func requestFitnessFile(fileType: Int, ids: [FitnessRecordID]) -> BerryCommand {
        var size = 0
        let list = ProtoWriter()
        for id in ids {
            size += id.length
            list.bytes(1, id.raw)
        }
        let status = ProtoWriter().uint(1, fileType).uint(2, 1).uint(3, size).message(4, list)
        let menu = ProtoWriter().message(56, status)
        return BerryCommand.wear(WearType.settingMenu, WearID.appControlFileSendStatus, field: WearField.settingMenu, body: menu, label: "requestFitnessFile")
    }

    public static func deleteFitnessIds(fileType: Int, ids: [FitnessRecordID]) -> BerryCommand {
        let list = ProtoWriter()
        for id in ids {
            list.bytes(1, id.raw)
        }
        let del = ProtoWriter().uint(1, fileType).message(2, list)
        let menu = ProtoWriter().message(60, del)
        return BerryCommand.wear(WearType.settingMenu, WearID.deleteFitnessIdList, field: WearField.settingMenu, body: menu, label: "deleteFitnessIds")
    }

    // MARK: Settings

    static func settingTime(_ minutes: Int) -> ProtoWriter {
        return ProtoWriter().uint(1, (minutes / 60) % 24).uint(2, minutes % 60)
    }

    public static func get(_ id: Int) -> BerryCommand {
        return BerryCommand.wear(WearType.settingMenu, id, label: "get-\(id)")
    }

    public static func setHeartRateMonitor(_ s: HeartRateSettings) -> BerryCommand {
        let m = ProtoWriter()
            .uint(1, s.enabled ? 0 : 1)
            .uint(2, s.intervalMinutes)
            .bool(3, s.highAlertEnabled)
            .uint(4, s.highAlertBpm)
        let menu = ProtoWriter().message(3, m)
        return BerryCommand.wear(WearType.settingMenu, WearID.setHeartRateMonitor, field: WearField.settingMenu, body: menu, label: "setHeartRate")
    }

    public static func setSpO2(_ s: SpO2Settings) -> BerryCommand {
        let m = ProtoWriter().uint(1, s.enabled ? 0 : 1).uint(2, s.intervalMinutes)
        let menu = ProtoWriter().message(30, m)
        return BerryCommand.wear(WearType.settingMenu, WearID.setSpO2Settings, field: WearField.settingMenu, body: menu, label: "setSpO2")
    }

    public static func setStress(_ s: StressSettings) -> BerryCommand {
        let m = ProtoWriter().bool(1, s.enabled).bool(2, s.relaxReminder)
        let menu = ProtoWriter().message(28, m)
        return BerryCommand.wear(WearType.settingMenu, WearID.setStressMode, field: WearField.settingMenu, body: menu, label: "setStress")
    }

    public static func setDoNotDisturb(_ s: ScheduleSettings) -> BerryCommand {
        let m = ProtoWriter()
            .bool(1, s.enabled)
            .message(2, settingTime(s.startMinutes))
            .message(3, settingTime(s.endMinutes))
        let menu = ProtoWriter().message(8, m)
        return BerryCommand.wear(WearType.settingMenu, WearID.setDoNotDisturb, field: WearField.settingMenu, body: menu, label: "setDND")
    }

    public static func setRaiseToWake(_ s: ScheduleSettings) -> BerryCommand {
        let m = ProtoWriter()
            .uint(1, s.enabled ? 0 : 2)
            .message(2, settingTime(s.startMinutes))
            .message(3, settingTime(s.endMinutes))
        let menu = ProtoWriter().message(1, m)
        return BerryCommand.wear(WearType.settingMenu, WearID.setWristScreen, field: WearField.settingMenu, body: menu, label: "setRaiseToWake")
    }

    public static func setSedentary(_ s: ReminderSettings) -> BerryCommand {
        let menu = ProtoWriter().message(4, reminder(s))
        return BerryCommand.wear(WearType.settingMenu, WearID.setSedentary, field: WearField.settingMenu, body: menu, label: "setSedentary")
    }

    public static func setDrinkWater(_ s: ReminderSettings) -> BerryCommand {
        let menu = ProtoWriter().message(7, reminder(s))
        return BerryCommand.wear(WearType.settingMenu, WearID.setDrinkWater, field: WearField.settingMenu, body: menu, label: "setDrinkWater")
    }

    static func reminder(_ s: ReminderSettings) -> ProtoWriter {
        return ProtoWriter()
            .bool(1, s.enabled)
            .message(2, settingTime(s.startMinutes))
            .message(3, settingTime(s.endMinutes))
            .uint(4, s.intervalMinutes)
            .bool(5, s.skipLunch)
    }

    public static func setAlarms(_ alarms: [WatchAlarm]) -> BerryCommand {
        let list = ProtoWriter()
        var index = 0
        for alarm in alarms {
            let data = ProtoWriter()
                .message(1, settingTime(alarm.minutes))
                .uint(2, alarm.weekdayMask)
                .bool(3, alarm.enabled)
                .string(4, String(alarm.label.prefix(20)))
                .uint(5, 0)
            list.message(1, ProtoWriter().uint(1, index).message(2, data))
            index += 1
        }
        let menu = ProtoWriter().message(5, list)
        return BerryCommand.wear(WearType.settingMenu, WearID.setAlarms, field: WearField.settingMenu, body: menu, label: "setAlarms")
    }

    public static func setUserProfile(_ p: UserProfile) -> BerryCommand {
        let profile = ProtoWriter()
            .uint(1, p.heightCm)
            .float(2, p.weightKg)
            .uint(4, p.age)
            .uint(5, p.sex.rawValue)
            .uint(7, p.calorieGoal)
            .uint(8, p.stepGoal)
            .uint(9, p.distanceGoalMeters)
            .uint(12, p.sleepGoalHours)
            .bool(14, true)
            .bool(15, true)
        let settings = ProtoWriter().message(5, profile)
        return BerryCommand.wear(WearType.userSettings, WearID.setUserProfile, field: WearField.userSettings, body: settings, label: "setUserProfile")
    }

    public static func setUnits(metric: Bool) -> BerryCommand {
        let settings = ProtoWriter().uint(3, metric ? 0 : 1)
        return BerryCommand.wear(WearType.userSettings, WearID.setUnitType, field: WearField.userSettings, body: settings, label: "setUnits")
    }

    // MARK: Watch faces

    public static func watchFaces() -> BerryCommand {
        return BerryCommand.wear(WearType.watchFace, WearID.getWatchFaces, label: "watchFaces")
    }

    public static func setWatchFace(_ id: String) -> BerryCommand {
        let body = ProtoWriter().string(2, id)
        return BerryCommand.wear(WearType.watchFace, WearID.setWatchFace, field: WearField.watchFace, body: body, label: "setWatchFace")
    }

    public static func removeWatchFace(_ id: String) -> BerryCommand {
        let body = ProtoWriter().string(2, id)
        return BerryCommand.wear(WearType.watchFace, WearID.removeWatchFace, field: WearField.watchFace, body: body, label: "removeWatchFace")
    }
}
