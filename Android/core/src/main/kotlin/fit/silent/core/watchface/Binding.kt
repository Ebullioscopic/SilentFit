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
 * [verified] bindings were taken from an official face for this watch; experimental bindings follow the
 * same numbering pattern and should be confirmed with the probe face (docs/WATCHFACE_FORMAT.md §5).
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
) {
    HOUR_HAND("hand.hour", "Hour hand", BindingKind.HAND, FaceRecord.TYPE_HAND, 0x05),
    MINUTE_HAND("hand.minute", "Minute hand", BindingKind.HAND, FaceRecord.TYPE_HAND, 0x06),
    SECOND_HAND("hand.second", "Second hand", BindingKind.HAND, FaceRecord.TYPE_HAND, 0x07, verified = false),
    MONTH("date.month", "Month", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x02, sample = "10"),
    DAY("date.day", "Day", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x03, sample = "25"),
    WEEKDAY("date.weekday", "Weekday", BindingKind.SPRITE, FaceRecord.TYPE_NUMBER, 0xFF, "PICBOX1", GlyphSet.WEEKDAYS, "SAT"),
    AM_PM("time.ampm", "AM / PM", BindingKind.SPRITE, FaceRecord.TYPE_IMAGE, 0x1F, glyphs = GlyphSet.AM_PM, sample = "PM"),
    STEPS("activity.steps", "Steps", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x41, sample = "8204", maxDigits = 5),
    CALORIES("activity.calories", "Calories", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x40, sample = "312", maxDigits = 4),
    HEART_RATE("health.heartRate", "Heart rate", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0xFF, "NUMBER4", sample = "72", maxDigits = 3, placeholderName = "N_DATA4"),
    BATTERY("device.battery", "Battery", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0xFF, "NUMBER2", sample = "86", maxDigits = 3),
    HOUR("time.hour", "Hour", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x05, sample = "10", verified = false),
    MINUTE("time.minute", "Minute", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x06, sample = "08", verified = false),
    SECOND("time.second", "Second", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x07, sample = "42", verified = false),
    YEAR("date.year", "Year", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x01, sample = "2026", maxDigits = 4, verified = false),
    DISTANCE("activity.distance", "Distance", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0x42, sample = "5", maxDigits = 3, verified = false),
    SPO2("health.spo2", "SpO₂", BindingKind.NUMBER, FaceRecord.TYPE_NUMBER, 0xFF, "NUMBER5", sample = "98", maxDigits = 3, verified = false),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}
