import Foundation

/// A simulated Berry watch that answers commands with protocol-accurate frames.
///
/// Used for UI previews, simulators, and as an end-to-end test of the codec.
public final class DemoTransport: WatchTransport {
    public static let address = "DE:MO:00:00:00:01"
    public let kind = "Demo watch"
    public var onReceive: (([UInt8]) -> Void)? = nil
    public var onStateChange: ((TransportState, String) -> Void)? = nil
    private let parser = BerryStreamParser()
    private var connected = false
    private var deviceSerial = 0
    private var faceIndex = 0

    public init() {
    }

    public func knownWatches() -> [DiscoveredWatch] {
        return [DiscoveredWatch(name: "Demo Watch", address: DemoTransport.address, transport: kind)]
    }

    public func connect(address: String) {
        onStateChange?(TransportState.connecting, "Connecting")
        Task {
            await Delay.ms(400)
            self.connected = true
            self.onStateChange?(TransportState.connected, "Connected")
        }
    }

    public func disconnect() {
        connected = false
        onStateChange?(TransportState.disconnected, "Disconnected")
    }

    public func send(_ bytes: [UInt8]) -> Bool {
        if !connected { return false }
        for unit in parser.feed(bytes) where unit.kind == BerryInboundKind.packet {
            respond(to: unit)
        }
        return true
    }

    private func deliver(_ frames: [[UInt8]], delayMs: Int = 60) {
        Task {
            for frame in frames {
                await Delay.ms(delayMs)
                self.onReceive?(frame)
            }
        }
    }

    private func frame(head: [UInt8], serial: Int, event: Int, type: Int, payload: [UInt8]) -> [UInt8] {
        var out = head
        out.append(UInt8(serial & 0xFF))
        out.append(UInt8(event))
        out.append(UInt8(type))
        out.append(contentsOf: ByteUtils.le16(payload.count))
        out.append(contentsOf: payload)
        return out
    }

    private func wear(_ type: Int, _ id: Int, field: Int = 0, body: ProtoWriter? = nil) -> [UInt8] {
        let w = ProtoWriter().uint(1, type).uint(2, id)
        if let body = body {
            w.message(field, body)
        }
        w.uint(100, 0)
        return w.bytes
    }

    private func reply(_ serial: Int, _ payload: [UInt8]) -> [UInt8] {
        return frame(head: BerryFrame.headAppRequest, serial: serial, event: 1, type: 3, payload: payload)
    }

    private func respond(to unit: BerryInbound) {
        let serial = unit.serial
        var frames: [[UInt8]] = [BerryFrame.ack(serial: serial)]
        if unit.event == BerryFrame.eventPrivate {
            let cmd = BerryResponses.privateCommand(unit.payload)
            var data: [UInt8] = []
            if cmd.cmdId == BerryPrivateID.hello {
                data = ByteUtils.bytes([0x7C, 0xE6, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x07, 0x1C, 0x00, 0x00])
            } else if cmd.cmdId == BerryPrivateID.userId {
                data = ByteUtils.bytes([0x7C, 0xE6, 0x27, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0xE0, 0xDE, 0x01, 0x00, 0x00, 0x00, 0xE0, 0xDE, 0x00, 0xC0])
            } else if cmd.cmdId == BerryPrivateID.bindRequest || cmd.cmdId == BerryPrivateID.bindConfirm {
                data = ByteUtils.zeros(1)
            }
            frames.append(frame(head: BerryFrame.headAppRequest, serial: serial, event: 3, type: 2, payload: BerryFrame.privatePayload(cmdId: cmd.cmdId, data: data)))
            deliver(frames)
            return
        }
        guard let message = WearMessage.parse(unit.payload) else { return }
        let t = message.type
        let id = message.id
        if t == WearType.device && id == WearID.getBattery {
            frames.append(reply(serial, wear(t, id, field: 5, body: ProtoWriter().message(2, battery()))))
        } else if t == WearType.device && id == WearID.getDeviceInfo {
            let info = ProtoWriter().string(1, "1.0.39").string(2, "59004").string(3, "de:mo:00:00:00:01").string(4, "10005").message(5, battery()).bool(10, true)
            frames.append(reply(serial, wear(t, id, field: 5, body: ProtoWriter().message(1, info))))
        } else if t == WearType.watchFace && id == WearID.getWatchFaces {
            frames.append(reply(serial, wear(t, id, field: 17, body: ProtoWriter().message(1, faces()))))
        } else if t == WearType.watchFace && id == WearID.setWatchFace {
            faceIndex = (faceIndex + 1) % 4
            frames.append(reply(serial, wear(t, id)))
        } else if t == WearType.notification && id == WearID.getQuickReplies {
            let list = ProtoWriter()
            for text in ["On my way", "Can I call you later?", "In a meeting right now.", "Sounds good!"] {
                list.message(1, ProtoWriter().string(1, text))
            }
            frames.append(reply(serial, wear(t, id, field: 12, body: ProtoWriter().message(5, list))))
        } else if t == WearType.settingMenu && id == WearID.getFitnessIdList {
            let list = ProtoWriter()
            for record in DemoData.records() {
                list.bytes(1, record.id.raw)
            }
            let returned = ProtoWriter().message(1, list)
            frames.append(reply(serial, wear(t, id, field: 14, body: ProtoWriter().message(59, returned))))
        } else if t == WearType.settingMenu && id == WearID.appControlFileSendStatus {
            frames.append(reply(serial, wear(t, id)))
            frames.append(contentsOf: largeFileFrames())
            deviceSerial += 1
            let status = ProtoWriter().uint(1, BerryFileType.daily).uint(2, 1).uint(3, 0)
            frames.append(frame(head: BerryFrame.headDeviceRequest, serial: deviceSerial, event: 1, type: 3, payload: wear(WearType.settingMenu, WearID.deviceControlFileSendStatus, field: 14, body: ProtoWriter().message(57, status))))
        } else if t == WearType.settingMenu && id == WearID.getHeartRateMonitor {
            let m = ProtoWriter().uint(1, 0).uint(2, 5).bool(3, true).uint(4, 125)
            frames.append(reply(serial, wear(t, id, field: 14, body: ProtoWriter().message(3, m))))
        } else if t == WearType.settingMenu && id == WearID.getSpO2Settings {
            let m = ProtoWriter().uint(1, 0).uint(2, 10)
            frames.append(reply(serial, wear(t, id, field: 14, body: ProtoWriter().message(30, m))))
        } else {
            frames.append(reply(serial, wear(t, id)))
        }
        deliver(frames)
    }

    private func battery() -> ProtoWriter {
        return ProtoWriter().uint(1, 76).uint(2, 2)
    }

    private func faces() -> ProtoWriter {
        let names = ["Pulse", "Analog Classic", "Digital Bold", "Activity Rings"]
        let list = ProtoWriter()
        var i = 0
        for name in names {
            list.message(1, ProtoWriter().string(1, "face-\(i)").string(2, name).bool(3, i == faceIndex).bool(4, i > 1))
            i += 1
        }
        return list
    }

    private func largeFileFrames() -> [[UInt8]] {
        var body: [UInt8] = []
        for record in DemoData.records() {
            body.append(contentsOf: record.bytes)
        }
        var file: [UInt8] = ByteUtils.le32(body.count)
        file.append(contentsOf: ByteUtils.bytes([0x00, 0x0F, 0, 0, 0, 0, 0, 0]))
        file.append(contentsOf: body)
        let firstLength = min(600, file.count)
        deviceSerial += 1
        var frames: [[UInt8]] = [frame(head: BerryFrame.headLargeFile, serial: deviceSerial, event: 4, type: BerryFileType.daily, payload: ByteUtils.slice(file, 0, firstLength))]
        var offset = firstLength
        var index = 1
        while offset < file.count {
            let end = min(file.count, offset + 600)
            var chunk: [UInt8] = ByteUtils.bytes([0x22, 0x48, 0x5A])
            chunk.append(contentsOf: ByteUtils.le16(end - offset))
            chunk.append(UInt8(index & 0xFF))
            chunk.append(contentsOf: ByteUtils.slice(file, offset, end))
            frames.append(chunk)
            offset = end
            index += 1
        }
        return frames
    }
}

/// Deterministic synthetic health records in the watch's binary format.
public enum DemoData {
    public struct Record {
        public let id: FitnessRecordID
        public let bytes: [UInt8]
    }

    public static func records(days: Int = 7, now: Date = Date()) -> [Record] {
        var out: [Record] = []
        let calendar = Calendar.current
        var d = days - 1
        while d >= 0 {
            guard let date = calendar.date(byAdding: .day, value: -d, to: now) else { break }
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            let y = c.year ?? 2026
            let m = c.month ?? 1
            let day = c.day ?? 1
            let seed = (day * 37 + m * 11) % 100
            let currentHour = d == 0 ? calendar.component(.hour, from: now) : 23
            out.append(record(y, m, day, FitnessDecoder.typeDaily, daily(seed, currentHour)))
            out.append(record(y, m, day, FitnessDecoder.typeHeartRate, heartRate(seed, currentHour)))
            out.append(record(y, m, day, FitnessDecoder.typeSpO2, periodic(144, base: 96, spread: 3, seed: seed, currentHour: currentHour, tail: ByteUtils.zeros(50) + ByteUtils.bytes([10, 0]))))
            out.append(record(y, m, day, FitnessDecoder.typeStress, periodic(144, base: 32, spread: 30, seed: seed, currentHour: currentHour, tail: ByteUtils.bytes([0, 0, 10]))))
            out.append(record(y, m, day, FitnessDecoder.typeStanding, hourlyFlags(seed, currentHour) + ByteUtils.bytes([60])))
            out.append(record(y, m, day, FitnessDecoder.typeActivityDuration, activity(seed, currentHour) + ByteUtils.bytes([60]) + ByteUtils.zeros(42)))
            if let date = calendar.date(from: c) {
                out.append(record(y, m, day, FitnessDecoder.typeSleep, sleep(dayStart: Int64(date.timeIntervalSince1970), seed: seed)))
            }
            d -= 1
        }
        return out
    }

    static func record(_ y: Int, _ m: Int, _ d: Int, _ type: Int, _ payload: [UInt8]) -> Record {
        var header: [UInt8] = ByteUtils.le16(y)
        header.append(contentsOf: ByteUtils.bytes([m, d, 0, 0, 0, type, 1]))
        let length = header.count + payload.count
        let time = ProtoWriter().uint(1, y).uint(2, m).uint(3, d).uint(4, 0).uint(5, 0).uint(6, 0)
        let raw = ProtoWriter().message(1, time).uint(2, type).uint(3, length).uint(4, 1).bytes
        let id = FitnessRecordID(raw: raw, year: y, month: m, day: d, functionType: type, length: length)
        return Record(id: id, bytes: header + payload)
    }

    static func u32(_ v: Int) -> [UInt8] {
        return ByteUtils.le32(v)
    }

    static func daily(_ seed: Int, _ currentHour: Int) -> [UInt8] {
        var steps: [Int] = []
        for h in 0..<24 {
            var v = 0
            if h <= currentHour && h >= 7 && h <= 21 {
                v = 150 + ((h * 97 + seed * 13) % 900)
                if h == 8 || h == 18 { v += 1400 }
            }
            steps.append(v)
        }
        var totalSteps = 0
        for v in steps { totalSteps += v }
        var out: [UInt8] = u32(totalSteps) + u32(totalSteps * 78 / 100) + u32(totalSteps / 25)
        for v in steps { out.append(contentsOf: ByteUtils.le16(v)) }
        for v in steps { out.append(contentsOf: ByteUtils.le16(v * 78 / 100)) }
        for v in steps { out.append(contentsOf: ByteUtils.le16(v / 25)) }
        out.append(contentsOf: ByteUtils.bytes([60, 60, 60]))
        out.append(contentsOf: ByteUtils.zeros(275 - out.count))
        return out
    }

    static func heartRate(_ seed: Int, _ currentHour: Int) -> [UInt8] {
        var out: [UInt8] = []
        for i in 0..<288 {
            let hour = i / 12
            if hour > currentHour {
                out.append(UInt8(0))
                continue
            }
            let asleep = hour < 6
            let base = asleep ? 56 : 72
            let wave = (i * 7 + seed) % 14
            let workout = (hour == 18 && i % 12 < 6) ? 45 : 0
            out.append(UInt8(base + wave + workout))
        }
        out.append(contentsOf: ByteUtils.bytes([130, 52, 5, 0, 0]))
        out.append(contentsOf: ByteUtils.zeros(48))
        return out
    }

    static func periodic(_ count: Int, base: Int, spread: Int, seed: Int, currentHour: Int, tail: [UInt8]) -> [UInt8] {
        var out: [UInt8] = []
        for i in 0..<count {
            let hour = i * 24 / count
            if hour > currentHour || i % 3 != 0 {
                out.append(UInt8(0))
            } else {
                out.append(UInt8(base + ((i * 13 + seed) % max(1, spread))))
            }
        }
        out.append(contentsOf: tail)
        return out
    }

    static func hourlyFlags(_ seed: Int, _ currentHour: Int) -> [UInt8] {
        var out: [UInt8] = []
        for h in 0..<24 {
            out.append(UInt8(h <= currentHour && h >= 8 && h <= 20 && (h + seed) % 3 != 0 ? 1 : 0))
        }
        return out
    }

    static func activity(_ seed: Int, _ currentHour: Int) -> [UInt8] {
        var out: [UInt8] = []
        for h in 0..<24 {
            var v = 0
            if h <= currentHour && h >= 7 && h <= 21 {
                v = (h * 5 + seed) % 12
                if h == 18 { v = 38 }
            }
            out.append(UInt8(v))
        }
        return out
    }

    static func sleep(dayStart: Int64, seed: Int) -> [UInt8] {
        let start = dayStart - 3600 + Int64((seed % 4) * 900)
        let stages = [1, 2, 1, 3, 2, 1, 0, 1, 2, 3, 1, 2, 1, 3, 1]
        let lengths = [25, 50, 35, 20, 45, 30, 6, 40, 35, 25, 30, 25, 40, 30, 20]
        var segments: [UInt8] = []
        var t = start
        var totals = [0, 0, 0, 0]
        var total = 0
        for i in 0..<stages.count {
            let minutes = lengths[i] + (seed + i) % 7
            segments.append(contentsOf: u32(Int(t)))
            segments.append(contentsOf: ByteUtils.le16(minutes))
            segments.append(UInt8(stages[i]))
            totals[stages[i]] += minutes
            total += minutes
            t += Int64(minutes * 60)
        }
        var out: [UInt8] = u32(Int(start)) + u32(Int(t))
        out.append(contentsOf: ByteUtils.le16(total))
        out.append(UInt8(60 + seed % 30))
        out.append(contentsOf: ByteUtils.le16(totals[0]))
        out.append(UInt8(totals[0] * 100 / total))
        out.append(contentsOf: ByteUtils.le16(totals[1]))
        out.append(contentsOf: ByteUtils.bytes([totals[1] * 100 / total, 0]))
        out.append(contentsOf: ByteUtils.le16(totals[2]))
        out.append(contentsOf: ByteUtils.bytes([totals[2] * 100 / total, 0]))
        out.append(contentsOf: ByteUtils.le16(totals[3]))
        out.append(contentsOf: ByteUtils.bytes([totals[3] * 100 / total, 0]))
        out.append(UInt8(stages.count))
        out.append(contentsOf: segments)
        return out
    }
}
