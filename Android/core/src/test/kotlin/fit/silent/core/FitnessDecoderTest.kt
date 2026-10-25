package fit.silent.core

import fit.silent.core.fitness.DemoData
import fit.silent.core.fitness.FitnessDecoder
import fit.silent.core.model.HealthDay
import fit.silent.core.protocol.BerryFrame
import fit.silent.core.protocol.BerryStreamParser
import fit.silent.core.protocol.ByteSink
import fit.silent.core.protocol.InboundKind
import fit.silent.core.protocol.ProtoMessage
import fit.silent.core.protocol.ProtoWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FitnessDecoderTest {
    @Test fun decodesSyntheticDailyFile() {
        val records = DemoData.records(days = 2)
        val days = HashMap<String, HealthDay>()
        val decoded = FitnessDecoder.decode(DemoData.file(records), records.map { it.id }, days)
        assertEquals(records.size, decoded)
        assertEquals(2, days.size)
        for (day in days.values) {
            assertEquals(24, day.hourlySteps.size)
            assertEquals(day.hourlySteps.sum(), day.steps)
            assertEquals(288, day.heartRate.values.size)
            val sleep = assertNotNull(day.sleep)
            assertEquals(sleep.totalMinutes, sleep.awakeMinutes + sleep.lightMinutes + sleep.deepMinutes + sleep.remMinutes)
            assertEquals(15, sleep.segments.size)
        }
    }

    @Test fun idListRoundTrip() {
        val records = DemoData.records(days = 1)
        val list = ProtoWriter()
        records.forEach { list.bytes(1, it.id.raw) }
        val menu = ProtoMessage.parse(ProtoWriter().message(59, ProtoWriter().message(1, list)).bytes)!!
        val ids = FitnessDecoder.parseIdList(menu)
        assertEquals(records.size, ids.size)
        assertEquals(FitnessDecoder.DAILY, ids[0].functionType)
        assertEquals(records[0].bytes.size, ids[0].length)
    }

    @Test fun streamParserReassemblesLargeFile() {
        val parser = BerryStreamParser()
        val file = ByteSink().u32(20).zeros(28).toByteArray().also { it[12] = 7 }
        val stream = ByteSink().bytes(BerryFrame.HEAD_FILE).u8(1).u8(4).u8(15).u16(16).bytes(file, 0, 16)
            .u8(0x22).u8(0x48).u8(0x5A).u16(16).u8(1).bytes(file, 16, 32).toByteArray()
        val units = parser.feed(stream)
        assertEquals(InboundKind.FILE, units.last().kind)
        assertEquals(32, units.last().payload.size)
        assertEquals(7, units.last().payload[12].toInt())
        assertTrue(units.first().kind == InboundKind.FILE_PROGRESS)
    }
}
