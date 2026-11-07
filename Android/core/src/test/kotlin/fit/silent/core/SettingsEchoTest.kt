package fit.silent.core

import fit.silent.core.fitness.SportIds
import fit.silent.core.fitness.WorkoutDecoder
import fit.silent.core.model.HeartRateSettings
import fit.silent.core.model.WatchAlarm
import fit.silent.core.protocol.ByteSink
import fit.silent.core.protocol.CommandPolicy
import fit.silent.core.protocol.Commands
import fit.silent.core.protocol.MenuField
import fit.silent.core.protocol.ProtoMessage
import fit.silent.core.protocol.ProtoWriter
import fit.silent.core.protocol.WearField
import fit.silent.core.protocol.WearMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettingsEchoTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }
    private fun menuBody(c: fit.silent.core.protocol.BerryCommand, field: Int) =
        WearMessage.parse(c.payload)!!.body(WearField.SETTING_MENU)!!.bytes(field)!!

    @Test fun heartRateWriteEchoesTheWatchReply() {
        // What the watch answered to GET heart rate: auto, 5 min, alert off at 120, sport alert on at 160, all-day, low 0.
        val reply = ProtoWriter().uint(1, 0).uint(2, 5).bool(3, false).uint(4, 120).bool(5, true).uint(6, 160).uint(7, 0).uint(8, 0).bytes
        val c = Commands.setHeartRate(HeartRateSettings(enabled = true, intervalMinutes = 10, highAlertBpm = 120), ProtoMessage.parse(reply))
        val expected = ProtoWriter().uint(1, 0).uint(2, 10).bool(3, false).uint(4, 120).bool(5, true).uint(6, 160).uint(7, 0).uint(8, 0).bytes
        assertEquals(hex(expected), hex(menuBody(c, MenuField.HEART_RATE)))
        assertEquals(CommandPolicy.Tier.ECHOED, CommandPolicy.tier(c))
        // Without a reply to edit the write stays experimental.
        assertEquals(CommandPolicy.Tier.EXPERIMENTAL, CommandPolicy.tier(Commands.setHeartRate(HeartRateSettings())))
    }

    @Test fun alarmWriteKeepsWatchIdsAndUnknownFields() {
        val time = ProtoWriter().uint(1, 7).uint(2, 0).uint(3, 0).uint(4, 0)
        val data = ProtoWriter().message(1, time).uint(2, 0x1F).bool(3, true).string(4, "Wake").uint(5, 1)
        val reply = ProtoWriter().message(1, ProtoWriter().uint(1, 3).message(2, data)).uint(2, 5).bytes
        val c = Commands.setAlarms(
            listOf(WatchAlarm(3, 7 * 60 + 30, 0x1F, true, "Wake"), WatchAlarm(4, 6 * 60, 0x60, true, "Gym")),
            ProtoMessage.parse(reply),
        )
        val list = ProtoMessage.parse(menuBody(c, MenuField.ALARMS))!!
        assertEquals(null, list.int(2), "max-count is reply-only")
        val items = list.messages(1)
        assertEquals(listOf(3, 4), items.map { it.int(1) })
        val first = items[0].message(2)!!
        assertEquals(30, first.message(1)!!.int(2))
        assertEquals(1, first.int(5), "label kept from the watch")
        val second = items[1].message(2)!!
        assertEquals(listOf(6, 0, 0, 0), (1..4).map { second.message(1)!!.int(it) })
        assertEquals(null, second.int(5), "new alarms carry no label, like the SDK")
        assertEquals(CommandPolicy.Tier.ECHOED, CommandPolicy.tier(c))
    }

    @Test fun sportRequestMatchesSdkLayout() {
        val ids = SportIds(ByteArray(14) { it.toByte() }, listOf(120, 80))
        val c = Commands.requestSportFile(ids)
        val status = WearMessage.parse(c.payload)!!.body(WearField.SETTING_MENU)!!.message(MenuField.FILE_SEND_STATUS)!!
        assertEquals(listOf(1 to 7L, 2 to 1L, 3 to 200L), status.fields.take(3).map { it.number to it.varint })
        assertContentEquals(ids.ids, status.bytes(5))
    }

    @Test fun decodesWorkoutSummary() {
        val start = 1_793_000_000L
        // Outdoor run (type 1), version 2, report record: id, flags, then the isData1 layout.
        val r = ByteSink(80).u32(start).u8(22).u8(0x02).u8((1 shl 2) or 1).zeros(5)
            .u32(start).u32(start + 1800).u32(1790).u32(5200).u16(310).zeros(12).u32(6100).zeros(2).u8(152).u8(181).u8(96).zeros(20).toByteArray()
        val w = assertNotNull(WorkoutDecoder.decodeRecord(r, start + 10_000))
        assertEquals("Outdoor running", w.name)
        assertEquals(1790, w.durationSeconds)
        assertEquals(5200, w.distanceMeters)
        assertEquals(310, w.calories)
        assertEquals(6100, w.steps)
        assertEquals(listOf(152, 181, 96), listOf(w.avgHeartRate, w.maxHeartRate, w.minHeartRate))
    }
}
