package fit.silent.core.watchface

/** What a glyph sprite contains, frame by frame (top to bottom). */
enum class GlyphSet(val frames: List<String>) {
    DIGITS((0..9).map { it.toString() }),
    WEEKDAYS(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")),
    AM_PM(listOf("AM", "PM")),
    PLACEHOLDER(listOf("--")),
}

enum class BindingKind { NUMBER, SPRITE, HAND }

/**
 * A data source the watch can render live.
 *
 * [verified] bindings appear in an official face for this watch (docs/WATCHFACE_FORMAT.md §4); the others are
 * guesses and are never compiled — an unknown id boot-looped the watch.
 */
enum class Binding(
    val key: String,
    val label: String,
    val kind: BindingKind,
    val recordType: Int,
    val dataId: Int,
    val recordName: String = "",
    val glyphs: GlyphSet = GlyphSet.DIGITS,
    val sample: String = "",
    val maxDigits: Int = 2,
    val verified: Boolean = true,
    /** Record shown instead of the number when the watch has no reading yet. */
    val placeholderName: String? = null,
    /** Watch values that are single digits: one record per digit, with these ids (most significant first). */
    val digitIds: List<Int>? = null,
) {
    HOUR_HAND("hand.hour", "Hour hand", BindingKind.HAND, FaceRecord.TYPE_HAND, 0x05),
    MINUTE_HAND("hand.minute", "Minute hand", BindingKind.HAND, FaceRecord.TYPE_HAND, 0x06),
    SECOND_HAND("hand.second", "Second hand", BindingKind.HAND, FaceRecord.TYPE_HAND, 0x07),
    MONTH("date.month", "Month", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x02, sample = "10"),
    DAY("date.day", "Day", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x03, sample = "25"),
    WEEKDAY("date.weekday", "Weekday", BindingKind.SPRITE, FaceRecord.TYPE_NUMBER, 0xFF, "PICBOX1", GlyphSet.WEEKDAYS, "SAT"),
    AM_PM("time.ampm", "AM / PM", BindingKind.SPRITE, FaceRecord.TYPE_IMAGE, 0x1F, glyphs = GlyphSet.AM_PM, sample = "PM"),
    STEPS("activity.steps", "Steps", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x41, sample = "8204", maxDigits = 5),
    CALORIES("activity.calories", "Calories", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x40, sample = "312", maxDigits = 4),
    HEART_RATE("health.heartRate", "Heart rate", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0xFF, "NUMBER4", sample = "72", maxDigits = 3, placeholderName = "N_DATA4"),
    BATTERY("device.battery", "Battery", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0xFF, "NUMBER2", sample = "86", maxDigits = 3),
    HOUR("time.hour", "Hour", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x08, sample = "10", digitIds = listOf(0x08, 0x09)),
    MINUTE("time.minute", "Minute", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x0A, sample = "08", digitIds = listOf(0x0A, 0x0B)),
    HOUR_TENS("time.hour.tens", "Hour, first digit", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x08, sample = "1", maxDigits = 1),
    HOUR_UNITS("time.hour.units", "Hour, second digit", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x09, sample = "0", maxDigits = 1),
    MINUTE_TENS("time.minute.tens", "Minute, first digit", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x0A, sample = "0", maxDigits = 1),
    MINUTE_UNITS("time.minute.units", "Minute, second digit", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x0B, sample = "8", maxDigits = 1),
    SECOND("time.second", "Second", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x07, sample = "42", verified = false),
    YEAR("date.year", "Year", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x01, sample = "2026", maxDigits = 4, verified = false),
    DISTANCE("activity.distance", "Distance", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x42, sample = "5", maxDigits = 3, verified = false),
    SPO2("health.spo2", "SpO₂", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0xFF, "NUMBER5", sample = "98", maxDigits = 3, verified = false),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}
