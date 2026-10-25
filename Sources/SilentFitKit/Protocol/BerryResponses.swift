import Foundation

/// A decoded SA.SEWear envelope.
public struct WearMessage {
    public let type: Int
    public let id: Int
    public let errorCode: Int
    public let root: ProtoMessage

    public var isError: Bool {
        return errorCode != 0
    }

    /// Payload sub-message stored in oneof field `field`.
    public func body(_ field: Int) -> ProtoMessage? {
        return root.message(field)
    }

    public static func parse(_ payload: [UInt8]) -> WearMessage? {
        guard let root = ProtoMessage.parse(payload), let type = root.int(WearField.type) else { return nil }
        return WearMessage(type: type, id: root.int(WearField.id) ?? 0, errorCode: root.int(WearField.errorCode) ?? 0, root: root)
    }
}

/// Result of private command 1 (protocol handshake).
public struct BerryProtocolInfo {
    public let deviceType: Int
    public let protocolVersion: String
    public let platformType: Int
}

/// Result of private command 2 (user id / bind state).
public struct BerryBindInfo {
    public let deviceType: Int
    public let firmware: String
    public let mac: String
    public let isBound: Bool
    public let isSameUser: Bool
}

public struct BerryPrivateReply {
    public let cmdId: Int
    public let data: [UInt8]
}

public enum BerryResponses {
    /// Private replies: cmdId (u16) + length (u16) + data. The watch's length field can be shorter
    /// than the data actually sent (e.g. bind info), so like the vendor SDK we keep every remaining byte.
    public static func privateCommand(_ payload: [UInt8]) -> BerryPrivateReply {
        return BerryPrivateReply(cmdId: ByteUtils.u16le(payload, 0), data: ByteUtils.slice(payload, 4, payload.count))
    }

    public static func protocolInfo(_ d: [UInt8]) -> BerryProtocolInfo {
        let version = "\(ByteUtils.u8(d, 4)).\(ByteUtils.u8(d, 3)).\(ByteUtils.u8(d, 2))"
        return BerryProtocolInfo(deviceType: ByteUtils.u16le(d, 0), protocolVersion: version, platformType: ByteUtils.u8(d, 9))
    }

    public static func bindInfo(_ d: [UInt8]) -> BerryBindInfo {
        let firmware = "\(ByteUtils.u8(d, 4)).\(ByteUtils.u8(d, 3)).\(ByteUtils.u8(d, 2))"
        var macParts: [String] = []
        var i = 10
        while i >= 5 {
            let hex = ByteUtils.hex([UInt8(ByteUtils.u8(d, i))])
            macParts.append(hex)
            i -= 1
        }
        let snLength = ByteUtils.u8(d, 17)
        let flags = ByteUtils.u8(d, 18 + snLength)
        return BerryBindInfo(
            deviceType: ByteUtils.u16le(d, 0),
            firmware: firmware,
            mac: macParts.joined(separator: ":"),
            isBound: (flags & 0x40) != 0,
            isSameUser: (flags & 0x80) != 0)
    }

    /// SEDeviceBatteryStatus { capacity = 1, charge_status = 2 }
    public static func battery(_ m: ProtoMessage) -> BatteryStatus {
        return BatteryStatus(level: m.int(1) ?? 0, state: ChargeState(rawValue: m.int(2) ?? 0) ?? ChargeState.unknown)
    }

    /// SEDeviceInfo { firmware_version = 1, equipment_number = 2, mac = 3, serial_number = 4, device_battery_status = 5 }
    public static func deviceInfo(_ m: ProtoMessage, into info: inout WatchInfo) -> BatteryStatus? {
        info.firmware = m.string(1) ?? info.firmware
        info.model = m.string(2) ?? info.model
        info.serial = m.string(4) ?? info.serial
        if let b = m.message(5) {
            return battery(b)
        }
        return nil
    }

    /// SAWatchFaceItem.List { list = 1 { id = 1, name = 2, is_current = 3, can_remove = 4 } }
    public static func watchFaces(_ faceRoot: ProtoMessage) -> [WatchFace] {
        guard let list = faceRoot.message(1) else { return [] }
        var faces: [WatchFace] = []
        for item in list.messages(1) {
            let id = item.string(1) ?? ""
            let name = item.string(2) ?? id
            faces.append(WatchFace(id: id, name: name.isEmpty ? id : name, isCurrent: item.bool(3) ?? false, canRemove: item.bool(4) ?? false))
        }
        return faces
    }

    /// SEQuickReply.SEList { list = 1 { text = 1 } }
    public static func quickReplies(_ notification: ProtoMessage) -> [String] {
        guard let list = notification.message(5) else { return [] }
        return list.messages(1).map { $0.string(1) ?? "" }.filter { !$0.isEmpty }
    }

    /// SEBasicData { steps = 1, calories = 2, distance = 3, heart_rate = 4, blood_oxygen = 5, pressure = 13 }
    public static func liveData(_ m: ProtoMessage) -> LiveData {
        return LiveData(steps: m.int(1) ?? 0, calories: m.int(2) ?? 0, distanceMeters: m.int(3) ?? 0, heartRate: m.int(4) ?? 0, spo2: m.int(5) ?? 0, stress: m.int(13) ?? 0)
    }

    static func minutes(_ time: ProtoMessage?) -> Int {
        guard let t = time else { return 0 }
        return (t.int(1) ?? 0) * 60 + (t.int(2) ?? 0)
    }

    public static func heartRate(_ m: ProtoMessage) -> HeartRateSettings {
        return HeartRateSettings(enabled: (m.int(1) ?? 0) == 0, intervalMinutes: m.int(2) ?? 5, highAlertEnabled: m.bool(3) ?? false, highAlertBpm: m.int(4) ?? 120)
    }

    public static func spo2(_ m: ProtoMessage) -> SpO2Settings {
        return SpO2Settings(enabled: (m.int(1) ?? 0) == 0, intervalMinutes: m.int(2) ?? 10)
    }

    public static func stress(_ m: ProtoMessage) -> StressSettings {
        return StressSettings(enabled: m.bool(1) ?? false, relaxReminder: m.bool(2) ?? false)
    }

    public static func doNotDisturb(_ m: ProtoMessage) -> ScheduleSettings {
        return ScheduleSettings(enabled: m.bool(1) ?? false, startMinutes: minutes(m.message(2)), endMinutes: minutes(m.message(3)))
    }

    public static func raiseToWake(_ m: ProtoMessage) -> ScheduleSettings {
        return ScheduleSettings(enabled: (m.int(1) ?? 0) != 2, startMinutes: minutes(m.message(2)), endMinutes: minutes(m.message(3)))
    }

    public static func reminder(_ m: ProtoMessage) -> ReminderSettings {
        return ReminderSettings(enabled: m.bool(1) ?? false, startMinutes: minutes(m.message(2)), endMinutes: minutes(m.message(3)), intervalMinutes: m.int(4) ?? 60, skipLunch: m.bool(5) ?? false)
    }

    public static func alarms(_ m: ProtoMessage) -> [WatchAlarm] {
        var out: [WatchAlarm] = []
        for item in m.messages(1) {
            guard let data = item.message(2) else { continue }
            out.append(WatchAlarm(id: "alarm-\(item.int(1) ?? out.count)", minutes: minutes(data.message(1)), weekdayMask: data.int(2) ?? 0, enabled: data.bool(3) ?? false, label: data.string(4) ?? "Alarm"))
        }
        return out
    }
}
