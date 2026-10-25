package fit.silent.core.fitness

import fit.silent.core.model.FitnessRecordId
import fit.silent.core.protocol.ByteSink
import fit.silent.core.protocol.ProtoWriter
import java.time.LocalDateTime
import java.time.ZoneId

/** Deterministic synthetic health records in the watch's binary format (demo mode and tests). */
object DemoData {
    class Record(val id: FitnessRecordId, val bytes: ByteArray)

    fun records(days: Int = 7, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): List<Record> {
        val out = ArrayList<Record>()
        for (d in days - 1 downTo 0) {
            val date = now.toLocalDate().minusDays(d.toLong())
            val (y, m, day) = Triple(date.year, date.monthValue, date.dayOfMonth)
            val seed = (day * 37 + m * 11) % 100
            val hour = if (d == 0) now.hour else 23
            out += record(y, m, day, FitnessDecoder.DAILY, daily(seed, hour))
            out += record(y, m, day, FitnessDecoder.HEART_RATE, heartRate(seed, hour))
            out += record(y, m, day, FitnessDecoder.SPO2, periodic(96, 3, seed, hour) + ByteArray(50) + byteArrayOf(10, 0))
            out += record(y, m, day, FitnessDecoder.STRESS, periodic(32, 30, seed, hour) + byteArrayOf(0, 0, 10))
            out += record(y, m, day, FitnessDecoder.STANDING, ByteArray(24) { h -> if (h <= hour && h in 8..20 && (h + seed) % 3 != 0) 1 else 0 } + byteArrayOf(60))
            out += record(y, m, day, FitnessDecoder.ACTIVITY, ByteArray(24) { h -> activity(h, seed, hour).toByte() } + byteArrayOf(60) + ByteArray(42))
            out += record(y, m, day, FitnessDecoder.SLEEP, sleep(date.atStartOfDay(zone).toEpochSecond(), seed))
        }
        return out
    }

    /** Concatenates records into a file exactly as the watch transfers it. */
    fun file(records: List<Record>): ByteArray {
        val body = ByteSink()
        records.forEach { body.bytes(it.bytes) }
        return ByteSink().u32(body.size).zeros(8).bytes(body.toByteArray()).toByteArray()
    }

    fun record(y: Int, m: Int, d: Int, type: Int, payload: ByteArray): Record {
        val header = ByteSink().u16(y).u8(m).u8(d).zeros(3).u8(type).u8(1).toByteArray()
        val length = header.size + payload.size
        val time = ProtoWriter().uint(1, y).uint(2, m).uint(3, d).uint(4, 0).uint(5, 0).uint(6, 0)
        val raw = ProtoWriter().message(1, time).uint(2, type).uint(3, length).uint(4, 1).bytes
        return Record(FitnessRecordId(raw, y, m, d, type, length), header + payload)
    }

    private fun daily(seed: Int, hour: Int): ByteArray {
        val steps = List(24) { h ->
            if (h <= hour && h in 7..21) 150 + (h * 97 + seed * 13) % 900 + if (h == 8 || h == 18) 1400 else 0 else 0
        }
        val total = steps.sum()
        val s = ByteSink().u32(total).u32(total * 78 / 100).u32(total / 25)
        steps.forEach { s.u16(it) }
        steps.forEach { s.u16(it * 78 / 100) }
        steps.forEach { s.u16(it / 25) }
        s.u8(60).u8(60).u8(60)
        return s.zeros(275 - s.size).toByteArray()
    }

    private fun heartRate(seed: Int, hour: Int): ByteArray {
        val s = ByteSink()
        for (i in 0 until 288) {
            val h = i / 12
            if (h > hour) { s.u8(0); continue }
            val base = if (h < 6) 56 else 72
            val workout = if (h == 18 && i % 12 < 6) 45 else 0
            s.u8(base + (i * 7 + seed) % 14 + workout)
        }
        return s.u8(130).u8(52).u8(5).zeros(50).toByteArray()
    }

    private fun periodic(base: Int, spread: Int, seed: Int, hour: Int) = ByteArray(144) { i ->
        if (i * 24 / 144 > hour || i % 3 != 0) 0 else (base + (i * 13 + seed) % maxOf(1, spread)).toByte()
    }

    private fun activity(h: Int, seed: Int, hour: Int) = if (h <= hour && h in 7..21) (if (h == 18) 38 else (h * 5 + seed) % 12) else 0

    private fun sleep(dayStart: Long, seed: Int): ByteArray {
        val start = dayStart - 3600 + (seed % 4) * 900
        val stages = intArrayOf(1, 2, 1, 3, 2, 1, 0, 1, 2, 3, 1, 2, 1, 3, 1)
        val lengths = intArrayOf(25, 50, 35, 20, 45, 30, 6, 40, 35, 25, 30, 25, 40, 30, 20)
        val segments = ByteSink()
        val totals = IntArray(4)
        var t = start
        for (i in stages.indices) {
            val minutes = lengths[i] + (seed + i) % 7
            segments.u32(t).u16(minutes).u8(stages[i])
            totals[stages[i]] += minutes
            t += minutes * 60L
        }
        val total = totals.sum()
        return ByteSink().u32(start).u32(t).u16(total).u8(60 + seed % 30)
            .u16(totals[0]).u8(totals[0] * 100 / total)
            .u16(totals[1]).u8(totals[1] * 100 / total).u8(0)
            .u16(totals[2]).u8(totals[2] * 100 / total).u8(0)
            .u16(totals[3]).u8(totals[3] * 100 / total).u8(0)
            .u8(stages.size).bytes(segments.toByteArray()).toByteArray()
    }
}
