import Testing
import Foundation
@testable import SilentFitKit

/// Frames below were captured from the vendor app talking to an NF 2 watch.
@Suite struct BerryProtocolTests {
    func hex(_ s: String) -> [UInt8] {
        return ByteUtils.fromHex(s)
    }

    @Test func helloFrameMatchesCapture() {
        let c = BerryCommands.hello()
        let frame = BerryFrame.encode(serial: 0, event: c.event, type: c.type, payload: c.payload)
        #expect(frame == hex("66 66 48 5A 00 03 02 04 00 01 00 00 00"))
    }

    @Test func setTimeMatchesCapture() {
        let date = Date(timeIntervalSince1970: 1792927664)
        let ist = TimeZone(secondsFromGMT: 19800)!
        let c = BerryCommands.setTime(date, timeZone: ist)
        let frame = BerryFrame.encode(serial: 4, event: c.event, type: c.type, payload: c.payload)
        #expect(frame == hex("66 66 48 5A 04 01 03 10 00 08 03 10 00 32 0A 0A 08 08 B0 CF F7 D6 06 10 2C"))
    }

    @Test func simpleCommandsMatchCapture() {
        #expect(BerryCommands.findWatch().payload == hex("08 08 10 01"))
        #expect(BerryCommands.battery().payload == hex("08 02 10 01"))
        #expect(BerryCommands.deviceInfo().payload == hex("08 02 10 00"))
        #expect(BerryCommands.quickReplies().payload == hex("08 09 10 05"))
        #expect(BerryCommands.callState(1).payload == hex("08 09 10 04 62 02 20 01"))
        #expect(BerryCommands.fitnessIdList(fileType: BerryFileType.daily).payload == hex("08 0B 10 8A 01 72 05 D2 03 02 08 0F"))
        #expect(BerryCommands.fitnessIdList(fileType: BerryFileType.sport).payload == hex("08 0B 10 8A 01 72 05 D2 03 02 08 07"))
    }

    @Test func musicInfoMatchesCapture() {
        let info = NowPlaying(state: PlaybackState.paused, title: "", volume: 13, maxVolume: 16)
        #expect(BerryCommands.musicInfo(info).payload == hex("08 0A 10 03 6A 10 0A 0E 08 03 12 00 28 0D 30 10 38 00 40 00 50 01"))
    }

    @Test func ackFrame() {
        #expect(BerryFrame.ack(serial: 0x5D) == hex("99 99 48 5A 5D 00 00 00"))
    }

    @Test func parsesBatteryResponseAcrossFragments() {
        let parser = BerryStreamParser()
        let stream = hex("99 99 48 5A 02 00 00 00 66 66 48 5A 02 01 03 0C 00 08 02 10 01 2A 06 12 04 08 52 10 02")
        var units: [BerryInbound] = []
        units += parser.feed(ByteUtils.slice(stream, 0, 5))
        units += parser.feed(ByteUtils.slice(stream, 5, 17))
        units += parser.feed(ByteUtils.slice(stream, 17, stream.count))
        #expect(units.count == 2)
        #expect(units[0].kind == BerryInboundKind.ack)
        #expect(units[1].kind == BerryInboundKind.packet)
        let message = WearMessage.parse(units[1].payload)!
        #expect(message.type == WearType.device)
        #expect(message.id == WearID.getBattery)
        let battery = BerryResponses.battery(message.body(5)!.message(2)!)
        #expect(battery.level == 82)
        #expect(battery.state == ChargeState.notCharging)
    }

    @Test func parsesWatchFindPhoneRequest() {
        let parser = BerryStreamParser()
        let units = parser.feed(hex("88 88 48 5A 03 01 03 08 00 08 08 10 00 5A 02 08 01"))
        #expect(units.count == 1)
        #expect(units[0].fromDevice)
        let message = WearMessage.parse(units[0].payload)!
        #expect(message.type == WearType.microFunction)
        #expect(message.body(11)?.int(1) == 1)
    }

    @Test func parsesErrorCode() {
        let message = WearMessage.parse(hex("08 0B 10 88 01 A0 06 00"))!
        #expect(message.id == WearID.appControlFileSendStatus)
        #expect(!message.isError)
    }

    @Test func protocolInfo() {
        let info = BerryResponses.protocolInfo(hex("7C E6 00 00 01 00 00 00 00 07 1C 00 00"))
        #expect(info.deviceType == 59004)
        #expect(info.protocolVersion == "1.0.0")
        #expect(info.platformType == 7)
    }

    @Test func bindFlags() {
        let base = "7C E6 27 00 01 01 02 03 04 05 06 01 02 03 04 05 06 00"
        #expect(BerryResponses.bindInfo(hex(base + " C0")).isSameUser)
        #expect(BerryResponses.bindInfo(hex(base + " C0")).isBound)
        #expect(!BerryResponses.bindInfo(hex(base + " 40")).isSameUser)
        #expect(!BerryResponses.bindInfo(hex(base + " 00")).isBound)
        #expect(BerryResponses.bindInfo(hex(base + " C0")).firmware == "1.0.39")
    }

    @Test func bindInfoIgnoresShortLengthField() {
        // Real replies declare length 0x16 but carry the serial number and flags after it.
        let payload = hex("02 00 16 00 7C E6 27 00 01 01 02 03 04 05 06 01 02 03 04 05 06 0C FF FF FF FF FF FF FF FF FF FF FF FF C0")
        let cmd = BerryResponses.privateCommand(payload)
        let info = BerryResponses.bindInfo(cmd.data)
        #expect(cmd.cmdId == 2)
        #expect(info.isBound)
        #expect(info.isSameUser)
    }

    @Test func userIdLayout() {
        let c = BerryCommands.userId("abc", phoneName: "P1", systemVersion: "16")
        #expect(c.payload == hex("02 00 0B 00 03 61 62 63 BB 02 50 31 02 31 36"))
    }

    @Test func varintRoundTrip() {
        let w = ProtoWriter().uint64(1, 1792927664).sint(2, -22).sint(3, 22).string(4, "héllo")
        let m = ProtoMessage.parse(w.bytes)!
        #expect(m.int64(1) == 1792927664)
        #expect(m.sint(2) == -22)
        #expect(m.sint(3) == 22)
        #expect(m.string(4) == "héllo")
    }

    @Test func floatRoundTrip() {
        let m = ProtoMessage.parse(ProtoWriter().float(2, 72.5).bytes)!
        #expect(m.float(2) == 72.5)
    }
}

@Suite struct FitnessDecoderTests {
    @Test func decodesSyntheticDailyFile() {
        let records = DemoData.records(days: 2)
        var body: [UInt8] = []
        var ids: [FitnessRecordID] = []
        for r in records {
            body += r.bytes
            ids.append(r.id)
        }
        var file = ByteUtils.le32(body.count) + ByteUtils.zeros(8)
        file += body
        var days: [String: HealthDay] = [:]
        let decoded = FitnessDecoder.decode(file: file, ids: ids, into: &days)
        #expect(decoded == records.count)
        #expect(days.count == 2)
        for day in days.values {
            #expect(day.hourlySteps.count == 24)
            var sum = 0
            for v in day.hourlySteps {
                sum += v
            }
            #expect(day.steps == sum)
            #expect(day.heartRate.values.count == 288)
            let sleep = day.sleep!
            #expect(sleep.totalMinutes == sleep.awakeMinutes + sleep.lightMinutes + sleep.deepMinutes + sleep.remMinutes)
            #expect(sleep.segments.count == 15)
        }
    }

    @Test func idListRoundTrip() {
        let records = DemoData.records(days: 1)
        let list = ProtoWriter()
        for r in records {
            list.bytes(1, r.id.raw)
        }
        let menu = ProtoMessage.parse(ProtoWriter().message(59, ProtoWriter().message(1, list)).bytes)!
        let ids = FitnessDecoder.parseIdList(menu)
        #expect(ids.count == records.count)
        #expect(ids[0].functionType == FitnessDecoder.typeDaily)
        #expect(ids[0].length == records[0].bytes.count)
    }

    @Test func streamParserReassemblesLargeFile() {
        let parser = BerryStreamParser()
        var file = ByteUtils.le32(20) + ByteUtils.zeros(28)
        file[12] = UInt8(7)
        var stream = BerryFrame.headLargeFile + ByteUtils.bytes([1, 4, 15]) + ByteUtils.le16(16) + ByteUtils.slice(file, 0, 16)
        stream += ByteUtils.bytes([0x22, 0x48, 0x5A]) + ByteUtils.le16(16) + ByteUtils.bytes([1]) + ByteUtils.slice(file, 16, 32)
        let units = parser.feed(stream)
        #expect(units.last?.kind == BerryInboundKind.largeFile)
        #expect(units.last?.payload.count == 32)
        #expect(Int(units.last?.payload[12] ?? 0) == 7)
    }
}
