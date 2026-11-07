package fit.silent.core.fitness

import fit.silent.core.protocol.Bytes
import fit.silent.core.protocol.MenuField
import fit.silent.core.protocol.ProtoMessage
import kotlinx.serialization.Serializable

/** A workout summary recorded by the watch. Times are Unix seconds. */
@Serializable
data class Workout(
    val key: String,
    val sportType: Int,
    val start: Long,
    val end: Long,
    val durationSeconds: Int,
    val distanceMeters: Int = 0,
    val calories: Int = 0,
    val steps: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val minHeartRate: Int = 0,
) {
    val name get() = WorkoutDecoder.sportName(sportType)
}

/** The watch's workout list: 7-byte ids back to back and each record's length (setting_menu 59 → 2). */
class SportIds(val ids: ByteArray, val lengths: List<Int>) {
    val count get() = minOf(ids.size / 7, lengths.size)
    val isEmpty get() = count == 0
}

/**
 * Decodes Berry sport files (large-file type 7), following the vendor SDK's SportParsing.
 *
 * Each record starts with its 7-byte id: `u32 time, u8 timezone*4, u8 (bit7 type high, bits4-6 type mid, bits0-3
 * version), u8 (bits2-6 type low, bits0-1 data type)`. Data type 1 is the summary ("report"), 0 per-minute points,
 * 2 GPS. Summary layouts differ by sport family and version; offsets below are SportParsing's, expressed relative
 * to the start-time field.
 */
object WorkoutDecoder {
    private const val FILE_PREFIX = 12
    const val DATA_REPORT = 1

    fun parseIds(settingMenu: ProtoMessage): SportIds? {
        val list = settingMenu.message(MenuField.FITNESS_ID_LIST)?.message(2) ?: return null
        return SportIds(list.bytes(1) ?: ByteArray(0), list.message(2)?.ints(1).orEmpty())
    }

    fun sportType(b5: Int, b6: Int) = ((b5 and 0x80) shr 7) * 256 + (((b5 and 0x70) shl 1) or ((b6 and 0x7C) shr 2))

    /** Splits [file] (with its 12-byte prefix) by the id list and decodes every summary record. */
    fun decode(file: ByteArray, ids: SportIds, nowSeconds: Long = System.currentTimeMillis() / 1000): List<Workout> {
        val out = ArrayList<Workout>()
        var offset = FILE_PREFIX
        for (i in 0 until ids.count) {
            val len = ids.lengths[i]
            if (len <= 0 || offset + len > file.size) break
            decodeRecord(file.copyOfRange(offset, offset + len), nowSeconds)?.let { out += it }
            offset += len
        }
        return out
    }

    fun decodeRecord(r: ByteArray, nowSeconds: Long = System.currentTimeMillis() / 1000): Workout? {
        if (r.size < 24) return null
        val idTime = Bytes.u32le(r, 0)
        val b5 = Bytes.u8(r, 5)
        val b6 = Bytes.u8(r, 6)
        if (b6 and 3 != DATA_REPORT) return null
        val type = sportType(b5, b6)
        val version = b5 and 0x0F
        val family = family(type)
        val s = startOffset(family, version)?.takeIf { plausible(r, it, nowSeconds) }
            ?: (8..20).firstOrNull { plausible(r, it, nowSeconds) }
            ?: return null
        fun u8(at: Int) = if (at in r.indices) Bytes.u8(r, at) else 0
        fun u16(at: Int) = if (at + 1 < r.size) Bytes.u16le(r, at) else 0
        fun u32(at: Int) = if (at + 3 < r.size) Bytes.u32le(r, at).toInt() else 0
        val start = Bytes.u32le(r, s)
        val end = Bytes.u32le(r, s + 4)
        val distanceFamily = family != Family.GENERIC && family != Family.OTHER
        val (stepsAt, hrAt) = when (family) {
            Family.RUN, Family.GPS_RUN, Family.MIXED -> s + 30 to s + 36
            Family.WALK -> s + 26 to s + 32
            Family.RIDE -> -1 to s + 30
            Family.GENERIC -> -1 to s + 14
            Family.RACKET -> s + 18 to s + 24
            Family.BALL -> -1 to s + 18
            Family.SWIM, Family.OTHER -> -1 to -1
        }
        fun hr(at: Int) = if (at < 0) 0 else u8(at).takeIf { it in 30..250 } ?: 0
        return Workout(
            key = "${idTime}_$type",
            sportType = type,
            start = start,
            end = end,
            durationSeconds = u32(s + 8),
            distanceMeters = if (distanceFamily) u32(s + 12) else 0,
            calories = if (distanceFamily) u16(s + 16) else u16(s + 12),
            steps = if (stepsAt >= 0) u32(stepsAt) else 0,
            avgHeartRate = hr(hrAt),
            maxHeartRate = hr(hrAt + 1),
            minHeartRate = hr(hrAt + 2),
        )
    }

    private fun plausible(r: ByteArray, at: Int, now: Long): Boolean {
        if (at < 0 || at + 12 > r.size) return false
        val start = Bytes.u32le(r, at)
        val end = Bytes.u32le(r, at + 4)
        val duration = Bytes.u32le(r, at + 8)
        return start in 1_500_000_000L..(now + 2 * 86_400) && end >= start && end - start <= 2 * 86_400 && duration <= end - start + 600
    }

    /** SportParsing's isData1…isData14 groups. */
    private enum class Family { RUN, WALK, RIDE, GENERIC, SWIM, GPS_RUN, RACKET, BALL, MIXED, OTHER }

    private fun family(t: Int) = when (t) {
        1, 2, 4, 5, 13, 139 -> Family.RUN
        3, 135 -> Family.WALK
        6, in 14..20, 124 -> Family.RIDE
        in 7..12, in 21..165 -> Family.GENERIC
        200, 201, 199 -> Family.SWIM
        205 -> Family.GPS_RUN
        206, 207, 208, in 218..243 -> Family.RACKET
        in 209..215 -> Family.BALL
        in 244..266 -> Family.MIXED
        else -> Family.OTHER
    }

    private fun startOffset(f: Family, v: Int): Int? = when (f) {
        Family.RUN -> if (v <= 3) 12 else 13
        Family.WALK -> if (v <= 2) 11 else 12
        Family.RIDE -> 12
        Family.GENERIC -> 10
        Family.SWIM -> if (v <= 2) 10 else 11
        Family.GPS_RUN -> if (v <= 2) 12 else 13
        Family.RACKET -> 11
        Family.BALL -> 10
        Family.MIXED -> 12
        Family.OTHER -> null
    }

    fun sportName(type: Int): String = (NAMES[type] ?: "workout").replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** NoiseFit's names for the watch's sport numbers (DataConverter.Q). */
    private val NAMES = mapOf(
        1 to "outdoor_running", 2 to "outdoor_walking", 3 to "indoor_running", 4 to "trekking", 5 to "trail_running",
        6 to "outdoor_cycling", 7 to "indoor_cycling", 8 to "freestyle", 9 to "basketball", 10 to "football", 11 to "table_tennis",
        12 to "badminton", 13 to "outdoor_hiking", 16 to "sailing", 17 to "skateboarding", 18 to "roller_skating", 21 to "pool_swimming",
        22 to "open_water_swimming", 23 to "core_training", 24 to "mixed_aerobics", 25 to "strength_training", 26 to "stretching",
        27 to "climbing_machine", 28 to "pilates", 29 to "flexibility_training", 30 to "indoor_fitness", 31 to "stepper",
        32 to "step_training", 33 to "gymnastics", 34 to "elliptical", 35 to "yoga", 39 to "cricket", 40 to "baseball",
        41 to "bowling", 42 to "squash", 45 to "volleyball", 46 to "handball", 52 to "dance", 53 to "zumba", 55 to "karate",
        56 to "boxing", 59 to "tai_chi", 61 to "taekwondo", 62 to "martial_arts", 64 to "hiit", 65 to "archery", 71 to "kayaking",
        79 to "rock_climbing", 83 to "stair_climbing", 85 to "aerobics", 90 to "weight_lifting", 91 to "deadlift", 93 to "sit_ups",
        94 to "functional_training", 102 to "fencing", 103 to "rugby", 104 to "hockey", 105 to "tennis", 106 to "billiards",
        119 to "track_and_field", 121 to "rowing_machine", 123 to "triathlon", 124 to "mountain_cycling", 125 to "kickboxing",
        126 to "skiing", 128 to "snowboarding", 134 to "golf", 135 to "indoor_walking", 200 to "pool_swimming",
        201 to "open_water_swimming", 202 to "rowing_machine", 206 to "indoor_running", 207 to "outdoor_running",
        208 to "outdoor_walking", 209 to "indoor_cycling", 210 to "outdoor_cycling", 211 to "badminton", 212 to "tennis",
        213 to "football", 214 to "cricket", 215 to "yoga", 218 to "freestyle", 219 to "swimming", 244 to "outdoor_cycling",
        245 to "indoor_cycling", 246 to "outdoor_running", 247 to "indoor_running", 248 to "swimming", 249 to "hiking",
        250 to "elliptical", 251 to "walking", 252 to "trail_running", 253 to "spinning_bike", 254 to "other", 255 to "rowing",
        256 to "golf", 257 to "football", 258 to "badminton", 259 to "tennis", 260 to "squash", 261 to "table_tennis",
        262 to "cricket", 263 to "boxing", 264 to "dumbbell", 265 to "basketball", 266 to "yoga",
    )
}
