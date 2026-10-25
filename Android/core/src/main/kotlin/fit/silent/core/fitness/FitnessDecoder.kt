package fit.silent.core.fitness

import fit.silent.core.model.FitnessRecordId
import fit.silent.core.model.HealthDay
import fit.silent.core.model.SampleSeries
import fit.silent.core.model.SleepSegment
import fit.silent.core.model.SleepStage
import fit.silent.core.model.SleepSummary
import fit.silent.core.protocol.Bytes
import fit.silent.core.protocol.MenuField
import fit.silent.core.protocol.ProtoMessage

/** Decodes Berry daily large-file transfers (type 15) into [HealthDay] fragments (docs/PROTOCOL.md §6). */
object FitnessDecoder {
    private const val RECORD_HEADER = 9
    private const val FILE_PREFIX = 12

    const val DAILY = 0
    const val SLEEP = 1
    const val HEART_RATE = 2
    const val SPO2 = 4
    const val STRESS = 6
    const val STANDING = 11
    const val ACTIVITY = 12

    fun parseIdList(settingMenu: ProtoMessage): List<FitnessRecordId> {
        val list = settingMenu.message(MenuField.FITNESS_ID_LIST)?.message(1) ?: return emptyList()
        return list.rawRepeated(1).mapNotNull { raw ->
            val item = ProtoMessage.parse(raw) ?: return@mapNotNull null
            val time = item.message(1)
            FitnessRecordId(raw, time?.int(1) ?: 0, time?.int(2) ?: 0, time?.int(3) ?: 0, item.int(2) ?: -1, item.int(3) ?: 0)
        }
    }

    /** Splits [file] into records using the ordered id list and merges each into [days]. Returns records decoded. */
    fun decode(file: ByteArray, ids: List<FitnessRecordId>, days: MutableMap<String, HealthDay>): Int {
        var decoded = 0
        var offset = FILE_PREFIX
        for (id in ids) {
            var start = offset
            if (!headerMatches(file, start, id)) {
                start = findHeader(file, id, FILE_PREFIX)
                if (start < 0) continue
            }
            val end = minOf(file.size, start + id.length)
            val payload = file.copyOfRange(minOf(start + RECORD_HEADER, end), end)
            offset = end
            val day = days[id.dayKey] ?: HealthDay(id.dayKey)
            val updated = apply(id.functionType, payload, day)
            if (updated != null) {
                days[id.dayKey] = updated
                decoded++
            }
        }
        return decoded
    }

    private fun headerMatches(f: ByteArray, at: Int, id: FitnessRecordId): Boolean {
        if (at < 0 || at + RECORD_HEADER > f.size) return false
        return Bytes.u16le(f, at) == id.year && Bytes.u8(f, at + 2) == id.month && Bytes.u8(f, at + 3) == id.day && Bytes.u8(f, at + 7) == id.functionType
    }

    private fun findHeader(f: ByteArray, id: FitnessRecordId, from: Int): Int {
        var i = from
        while (i + RECORD_HEADER <= f.size) {
            if (headerMatches(f, i, id)) return i
            i++
        }
        return -1
    }

    fun apply(type: Int, p: ByteArray, day: HealthDay): HealthDay? = when (type) {
        DAILY -> if (p.size < 156) null else day.copy(
            steps = Bytes.u32le(p, 0).toInt(),
            distanceMeters = Bytes.u32le(p, 4).toInt(),
            calories = Bytes.u32le(p, 8).toInt(),
            hourlySteps = u16s(p, 12, 24),
            hourlyDistance = u16s(p, 60, 24),
            hourlyCalories = u16s(p, 108, 24),
        )
        HEART_RATE -> if (p.size < 291) null else day.copy(heartRate = SampleSeries(5, u8s(p, 0, 288)))
        SPO2 -> if (p.size < 144) null else day.copy(spo2 = SampleSeries(10, u8s(p, 0, 144)))
        STRESS -> if (p.size < 147) null else day.copy(stress = SampleSeries(10, u8s(p, 0, 144)))
        STANDING -> if (p.size < 24) null else day.copy(standingHours = u8s(p, 0, 24))
        ACTIVITY -> if (p.size < 24) null else day.copy(activeMinutesByHour = u8s(p, 0, 24))
        SLEEP -> decodeSleep(p)?.let { day.copy(sleep = it) }
        else -> null
    }

    fun decodeSleep(p: ByteArray): SleepSummary? {
        if (p.size < 27) return null
        val total = Bytes.u16le(p, 8)
        if (total == 0) return null
        val segments = (0 until Bytes.u8(p, 26)).mapNotNull { i ->
            val at = 27 + i * 7
            if (at + 7 > p.size) return@mapNotNull null
            val minutes = Bytes.u16le(p, at + 4)
            if (minutes <= 0) null else SleepSegment(Bytes.u32le(p, at), minutes, SleepStage.of(Bytes.u8(p, at + 6)))
        }
        return SleepSummary(
            start = Bytes.u32le(p, 0), end = Bytes.u32le(p, 4), totalMinutes = total, score = Bytes.u8(p, 10),
            awakeMinutes = Bytes.u16le(p, 11), lightMinutes = Bytes.u16le(p, 14), deepMinutes = Bytes.u16le(p, 18),
            remMinutes = Bytes.u16le(p, 22), segments = segments,
        )
    }

    private fun u16s(p: ByteArray, at: Int, n: Int) = List(n) { Bytes.u16le(p, at + it * 2) }
    private fun u8s(p: ByteArray, at: Int, n: Int) = List(n) { Bytes.u8(p, at + it) }
}
