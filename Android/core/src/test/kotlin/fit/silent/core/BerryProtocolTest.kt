package fit.silent.core

import fit.silent.core.model.ChargeState
import fit.silent.core.model.NowPlaying
import fit.silent.core.model.PlaybackState
import fit.silent.core.protocol.BerryFrame
import fit.silent.core.protocol.BerryStreamParser
import fit.silent.core.protocol.Bytes
import fit.silent.core.protocol.Commands
import fit.silent.core.protocol.DeviceId
import fit.silent.core.protocol.FileType
import fit.silent.core.protocol.InboundKind
import fit.silent.core.protocol.LargeFilePlan
import fit.silent.core.protocol.ProtoMessage
import fit.silent.core.protocol.ProtoWriter
import fit.silent.core.protocol.Responses
import fit.silent.core.protocol.WearMessage
import fit.silent.core.protocol.WearType
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Frames below were captured from the vendor app talking to an NF 2 watch. */
class BerryProtocolTest {
    private fun hex(s: String) = Bytes.fromHex(s)
    private fun frame(c: fit.silent.core.protocol.BerryCommand, serial: Int) = BerryFrame.encode(serial, c.event, c.type, c.payload)

    @Test fun helloFrameMatchesCapture() {
        assertContentEquals(hex("66 66 48 5A 00 03 02 04 00 01 00 00 00"), frame(Commands.hello(), 0))
    }

    @Test fun setTimeMatchesCapture() {
        val c = Commands.setTime(1792927664L * 1000, TimeZone.getTimeZone("Asia/Kolkata"))
        assertContentEquals(hex("66 66 48 5A 04 01 03 10 00 08 03 10 00 32 0A 0A 08 08 B0 CF F7 D6 06 10 2C"), frame(c, 4))
    }

    @Test fun simpleCommandsMatchCapture() {
        assertContentEquals(hex("08 08 10 01"), Commands.findWatch().payload)
        assertContentEquals(hex("08 02 10 01"), Commands.battery().payload)
        assertContentEquals(hex("08 02 10 00"), Commands.deviceInfo().payload)
        assertContentEquals(hex("08 09 10 05"), Commands.quickReplies().payload)
        assertContentEquals(hex("08 09 10 04 62 02 20 01"), Commands.callState(1).payload)
        assertContentEquals(hex("08 0B 10 8A 01 72 05 D2 03 02 08 0F"), Commands.fitnessIdList(FileType.DAILY).payload)
        assertContentEquals(hex("08 0B 10 8A 01 72 05 D2 03 02 08 07"), Commands.fitnessIdList(FileType.SPORT).payload)
        assertContentEquals(hex("08 0E 10 00"), Commands.watchFaces().payload)
    }

    @Test fun musicInfoMatchesCapture() {
        val info = NowPlaying(state = PlaybackState.PAUSED, volume = 13, maxVolume = 16)
        assertContentEquals(hex("08 0A 10 03 6A 10 0A 0E 08 03 12 00 28 0D 30 10 38 00 40 00 50 01"), Commands.musicInfo(info).payload)
    }

    @Test fun ackFrame() = assertContentEquals(hex("99 99 48 5A 5D 00 00 00"), BerryFrame.ack(0x5D))

    @Test fun parsesBatteryAcrossFragments() {
        val parser = BerryStreamParser()
        val stream = hex("99 99 48 5A 02 00 00 00 66 66 48 5A 02 01 03 0C 00 08 02 10 01 2A 06 12 04 08 52 10 02")
        val units = parser.feed(stream.copyOfRange(0, 5)) + parser.feed(stream.copyOfRange(5, 17)) + parser.feed(stream.copyOfRange(17, stream.size))
        assertEquals(2, units.size)
        assertEquals(InboundKind.ACK, units[0].kind)
        val message = WearMessage.parse(units[1].payload)!!
        assertEquals(WearType.DEVICE, message.type)
        assertEquals(DeviceId.BATTERY, message.id)
        val battery = Responses.battery(message.body(5)!!.message(2)!!)
        assertEquals(82, battery.level)
        assertEquals(ChargeState.NOT_CHARGING, battery.state)
    }

    @Test fun resynchronisesAfterGarbage() {
        val parser = BerryStreamParser()
        val units = parser.feed(hex("01 02 03 88 88 48 5A 03 01 03 08 00 08 08 10 00 5A 02 08 01"))
        assertEquals(1, units.size)
        assertTrue(units[0].fromDevice)
        assertEquals(1, WearMessage.parse(units[0].payload)!!.body(11)?.int(1))
    }

    @Test fun parsesErrorCode() {
        val ok = WearMessage.parse(hex("08 0B 10 88 01 A0 06 00"))!!
        assertFalse(ok.isError)
        val failed = WearMessage.parse(hex("08 0B 10 06 A0 06 03"))!!
        assertTrue(failed.isError)
    }

    @Test fun protocolInfo() {
        val info = Responses.protocolInfo(hex("7C E6 00 00 01 00 00 00 00 07 1C 00 00"))
        assertEquals(59004, info.deviceType)
        assertEquals("1.0.0", info.protocolVersion)
        assertEquals(7, info.platformType)
    }

    @Test fun bindFlags() {
        val base = "7C E6 27 00 01 01 02 03 04 05 06 01 02 03 04 05 06 00"
        assertTrue(Responses.bindInfo(hex("$base C0")).isSameUser)
        assertTrue(Responses.bindInfo(hex("$base C0")).isBound)
        assertFalse(Responses.bindInfo(hex("$base 40")).isSameUser)
        assertFalse(Responses.bindInfo(hex("$base 00")).isBound)
        assertEquals("1.0.39", Responses.bindInfo(hex("$base C0")).firmware)
    }

    @Test fun bindInfoIgnoresShortLengthField() {
        val reply = Responses.privateReply(hex("02 00 16 00 7C E6 27 00 01 01 02 03 04 05 06 01 02 03 04 05 06 0C FF FF FF FF FF FF FF FF FF FF FF FF C0"))
        assertEquals(2, reply.cmdId)
        assertTrue(Responses.bindInfo(reply.data).isBound)
    }

    @Test fun userIdLayout() {
        assertContentEquals(hex("02 00 0B 00 03 61 62 63 BB 02 50 31 02 31 36"), Commands.userId("abc", "P1", "16").payload)
    }

    @Test fun varintAndFloatRoundTrip() {
        val m = ProtoMessage.parse(ProtoWriter().uint64(1, 1792927664).sint(2, -22).sint(3, 22).string(4, "héllo").float(5, 72.5f).bytes)!!
        assertEquals(1792927664L, m.long(1))
        assertEquals(-22, m.sint(2))
        assertEquals(22, m.sint(3))
        assertEquals("héllo", m.string(4))
        assertEquals(72.5f, m.float(5))
    }

    @Test fun prepareFaceInstallLayout() {
        val m = WearMessage.parse(Commands.prepareFaceInstall("9000001", 54596).payload)!!
        assertEquals(WearType.WATCH_FACE, m.type)
        assertEquals(4, m.id)
        val info = m.body(17)!!.message(6)!!
        assertEquals("9000001", info.string(1))
        assertEquals(54596, info.int(2))
    }

    @Test fun largeFilePlanMatchesVendorSlicing() {
        assertEquals(4950, LargeFilePlan.sliceFor(4096))
        assertEquals(990, LargeFilePlan.sliceFor(1))
        assertEquals(1980, LargeFilePlan.sliceFor(990))
        val file = ByteArray(10_000) { it.toByte() }
        val plan = LargeFilePlan(FileType.WATCH_FACE, file, 4096, 7)
        val first = plan.chunks.first()
        assertContentEquals(hex("66 66 48 5A 07 02 05"), first.copyOfRange(0, 7))
        assertEquals(plan.sliceLength, first.size)
        assertEquals(4941, Bytes.u16le(first, 7))
        assertContentEquals(hex("10 27 00 00 05 00 00 00 00 00 00 00"), first.copyOfRange(9, 21))
        val reassembled = first.copyOfRange(9, first.size) + plan.chunks.drop(1).fold(ByteArray(0)) { a, b -> a + b }
        assertContentEquals(plan.payload, reassembled)
        assertTrue(plan.chunks.drop(1).dropLast(1).all { it.size == plan.sliceLength })
        assertEquals((plan.totalBytes + 4095) / 4096, plan.checkpoints)
    }
}
