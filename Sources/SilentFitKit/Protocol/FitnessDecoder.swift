import Foundation

/// Decodes Berry daily large-file transfers (DAILY type 15) into `HealthDay` fragments.
///
/// Layouts were recovered by comparing raw transfers with the vendor parser's decoded
/// output; see docs/PROTOCOL.md §6.
public enum FitnessDecoder {
    static let recordHeaderSize = 9
    static let filePrefixSize = 12

    public static let typeDaily = 0
    public static let typeSleep = 1
    public static let typeHeartRate = 2
    public static let typeSpO2 = 4
    public static let typeStress = 6
    public static let typeStanding = 11
    public static let typeActivityDuration = 12

    /// Parses the id-list reply (SEBigFileFitnessReturnIdList.fitness_type_id_list).
    public static func parseIdList(_ settingMenu: ProtoMessage) -> [FitnessRecordID] {
        guard let returned = settingMenu.message(59), let list = returned.message(1) else { return [] }
        var ids: [FitnessRecordID] = []
        for field in list.fields where field.number == 1 && field.wireType == 2 {
            guard let item = ProtoMessage.parse(field.data) else { continue }
            let time = item.message(1)
            ids.append(FitnessRecordID(
                raw: field.data,
                year: time?.int(1) ?? 0,
                month: time?.int(2) ?? 0,
                day: time?.int(3) ?? 0,
                functionType: item.int(2) ?? -1,
                length: item.int(3) ?? 0))
        }
        return ids
    }

    /// Splits the transferred file into records using the ordered id list and decodes each.
    public static func decode(file: [UInt8], ids: [FitnessRecordID], into days: inout [String: HealthDay]) -> Int {
        var decoded = 0
        var offset = filePrefixSize
        for id in ids {
            var start = offset
            if !headerMatches(file, start, id) {
                start = findHeader(file, id, from: filePrefixSize)
                if start < 0 { continue }
            }
            let end = min(file.count, start + id.length)
            let payload = ByteUtils.slice(file, start + recordHeaderSize, end)
            offset = end
            var day = days[id.dayKey] ?? HealthDay(id: id.dayKey)
            if apply(type: id.functionType, payload: payload, to: &day) {
                decoded += 1
            }
            days[id.dayKey] = day
        }
        return decoded
    }

    static func headerMatches(_ file: [UInt8], _ at: Int, _ id: FitnessRecordID) -> Bool {
        if at < 0 || at + recordHeaderSize > file.count { return false }
        return ByteUtils.u16le(file, at) == id.year
            && Int(file[at + 2]) == id.month
            && Int(file[at + 3]) == id.day
            && Int(file[at + 7]) == id.functionType
    }

    static func findHeader(_ file: [UInt8], _ id: FitnessRecordID, from: Int) -> Int {
        var i = from
        while i + recordHeaderSize <= file.count {
            if headerMatches(file, i, id) { return i }
            i += 1
        }
        return -1
    }

    public static func apply(type: Int, payload p: [UInt8], to day: inout HealthDay) -> Bool {
        if type == typeDaily {
            if p.count < 156 { return false }
            day.steps = Int(ByteUtils.u32le(p, 0))
            day.distanceMeters = Int(ByteUtils.u32le(p, 4))
            day.calories = Int(ByteUtils.u32le(p, 8))
            day.hourlySteps = u16Array(p, 12, 24)
            day.hourlyDistance = u16Array(p, 60, 24)
            day.hourlyCalories = u16Array(p, 108, 24)
            return true
        }
        if type == typeHeartRate {
            if p.count < 291 { return false }
            day.heartRate = series(values: u8Array(p, 0, 288), interval: 5)
            return true
        }
        if type == typeSpO2 {
            if p.count < 144 { return false }
            day.spo2 = series(values: u8Array(p, 0, 144), interval: 10)
            return true
        }
        if type == typeStress {
            if p.count < 147 { return false }
            day.stress = series(values: u8Array(p, 0, 144), interval: 10)
            return true
        }
        if type == typeStanding {
            if p.count < 24 { return false }
            day.standingHours = u8Array(p, 0, 24)
            return true
        }
        if type == typeActivityDuration {
            if p.count < 24 { return false }
            day.activeMinutesByHour = u8Array(p, 0, 24)
            return true
        }
        if type == typeSleep {
            guard let sleep = decodeSleep(p) else { return false }
            day.sleep = sleep
            return true
        }
        return false
    }

    public static func decodeSleep(_ p: [UInt8]) -> SleepSummary? {
        if p.count < 27 { return nil }
        let count = Int(p[26])
        var segments: [SleepSegment] = []
        var i = 0
        while i < count {
            let at = 27 + i * 7
            if at + 7 > p.count { break }
            let minutes = ByteUtils.u16le(p, at + 4)
            if minutes > 0 {
                let stage = SleepStage(rawValue: Int(p[at + 6])) ?? SleepStage.awake
                segments.append(SleepSegment(start: ByteUtils.u32le(p, at), minutes: minutes, stage: stage))
            }
            i += 1
        }
        let total = ByteUtils.u16le(p, 8)
        if total == 0 { return nil }
        return SleepSummary(
            start: ByteUtils.u32le(p, 0),
            end: ByteUtils.u32le(p, 4),
            totalMinutes: total,
            score: Int(p[10]),
            awakeMinutes: ByteUtils.u16le(p, 11),
            lightMinutes: ByteUtils.u16le(p, 14),
            deepMinutes: ByteUtils.u16le(p, 18),
            remMinutes: ByteUtils.u16le(p, 22),
            segments: segments)
    }

    static func series(values: [Int], interval: Int) -> SampleSeries {
        var lo = 0
        var hi = 0
        for v in values where v > 0 {
            if lo == 0 || v < lo { lo = v }
            if v > hi { hi = v }
        }
        return SampleSeries(interval: interval, values: values, min: lo, max: hi)
    }

    static func u16Array(_ p: [UInt8], _ offset: Int, _ count: Int) -> [Int] {
        var out: [Int] = []
        var i = 0
        while i < count {
            out.append(ByteUtils.u16le(p, offset + i * 2))
            i += 1
        }
        return out
    }

    static func u8Array(_ p: [UInt8], _ offset: Int, _ count: Int) -> [Int] {
        var out: [Int] = []
        var i = 0
        while i < count {
            out.append(ByteUtils.u8(p, offset + i))
            i += 1
        }
        return out
    }
}
