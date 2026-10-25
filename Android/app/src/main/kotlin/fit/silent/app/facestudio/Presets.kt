package fit.silent.app.facestudio

import fit.silent.core.watchface.Background
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceSpec

data class Preset(val key: String, val title: String, val description: String, val spec: FaceSpec, val experimental: Boolean = false)

/**
 * Built-in designs for the NF 2's 240×280 LCD. The panel is low resolution, so every preset follows the same
 * legibility rules: strokes ≥ 3 px, hands 7–10 px, labels ≥ 13 px bold, values ≥ 20 px, LED dots ≥ 3 px pitch.
 */
object Presets {
    private const val W = 240
    private const val CX = W / 2
    private const val CY = 140
    private const val RED = "#D71921"
    private const val WHITE = "#FFFFFF"
    private const val GREY = "#A0A0A0"
    private const val DIM = "#3A3A3A"
    private const val GHOST = "#262626"
    private const val INK = "#111111"

    // ------------------------------------------------------------------ building blocks

    /** Hour, minute and red seconds hands with a centre cap; all images point at 3 o'clock as the watch expects. */
    private fun hands(
        hourStyle: String = "bar", minuteStyle: String = hourStyle, color: String = WHITE, accent: String = RED,
        hourWidth: Float = 10f, minuteWidth: Float = 7f, seconds: Boolean = true, cap: String = accent,
    ) = buildList {
        add(FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key, handStyle = hourStyle, color = color, length = 60, tail = 12, stroke = hourWidth, pivotX = CX, pivotY = CY, size = 0f))
        add(FaceElement("minute", "hand", bind = Binding.MINUTE_HAND.key, handStyle = minuteStyle, color = color, length = 94, tail = 14, stroke = minuteWidth, pivotX = CX, pivotY = CY, size = 0f))
        if (seconds) add(FaceElement("second", "hand", bind = Binding.SECOND_HAND.key, handStyle = "line", color = accent, length = 100, tail = 22, stroke = 3f, pivotX = CX, pivotY = CY, size = 0f))
        add(FaceElement("cap", "shape", CX - 7, CY - 7, 14, 14, shape = "circle", color = cap))
    }

    /** Twelve bold baton ticks, optionally with sixty minute marks. */
    private fun ticks(color: String = WHITE, minutes: String? = DIM, inset: Int = 6) = buildList {
        val size = W - inset * 2
        if (minutes != null) add(FaceElement("minutes", "shape", inset, CY - size / 2, size, size, shape = "ticks", count = 60, size = 7f, stroke = 2f, color = minutes))
        add(FaceElement("hours", "shape", inset, CY - size / 2, size, size, shape = "ticks", count = 12, size = 16f, stroke = 5f, color = color))
    }

    private fun label(id: String, text: String, x: Int, y: Int, w: Int = 0, color: String = GREY, align: String = "left") =
        FaceElement(id, "text", x, y, w, text = text, font = "space-mono", size = 13f, weight = 700, color = color, tracking = 0.08f, align = align)

    private fun dateLine(y: Int, color: String = WHITE, accent: String = RED) = listOf(
        FaceElement("weekday", "sprite", 40, y, 76, bind = Binding.WEEKDAY.key, font = "space-mono", size = 18f, weight = 700, color = GREY, align = "right"),
        FaceElement("dateDot", "shape", CX - 3, y + 10, 6, 6, shape = "circle", color = accent),
        FaceElement("day", "number", 128, y, bind = Binding.DAY.key, font = "space-grotesk", size = 20f, weight = 700, color = color),
    )

    // ------------------------------------------------------------------ analogue

    val dotMatrix = Preset(
        "dot-matrix", "Dot Matrix", "Nothing's dotted dial with bold dot hands, Doto 12 and a red seconds hand.",
        FaceSpec(
            name = "Dot Matrix",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("ring", "shape", 8, CY - 112, 224, 224, shape = "dot-ring", count = 60, stroke = 2.4f, color = "#6A6A6A"),
                FaceElement("twelve", "text", 0, 42, W, text = "12", font = "doto", size = 40f, weight = 700, align = "center", color = WHITE),
            ) + dateLine(196) + listOf(
                FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key, handStyle = "dots", color = WHITE, length = 58, stroke = 9f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("minute", "hand", bind = Binding.MINUTE_HAND.key, handStyle = "dots", color = WHITE, length = 92, stroke = 7f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("second", "hand", bind = Binding.SECOND_HAND.key, handStyle = "line", color = RED, length = 100, tail = 22, stroke = 3f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("cap", "shape", CX - 7, CY - 7, 14, 14, shape = "circle", color = RED),
            ),
        ),
    )

    val instrument = Preset(
        "instrument", "Instrument", "Bold batons, big 12-3-6-9 numerals and two readouts — legible at a glance.",
        FaceSpec(
            name = "Instrument",
            background = Background("#000000"),
            elements = ticks() + listOf(
                FaceElement("n12", "text", 0, 34, W, text = "12", font = "space-grotesk", size = 30f, weight = 700, align = "center"),
                FaceElement("n6", "text", 0, 206, W, text = "6", font = "space-grotesk", size = 30f, weight = 700, align = "center"),
                FaceElement("n9", "text", 30, 122, text = "9", font = "space-grotesk", size = 30f, weight = 700),
                FaceElement("n3", "text", 192, 122, text = "3", font = "space-grotesk", size = 30f, weight = 700),
                label("bpmLabel", "BPM", 52, 160, 50, align = "center"),
                FaceElement("hr", "number", 52, 176, 50, bind = Binding.HEART_RATE.key, font = "space-grotesk", size = 22f, weight = 700, color = RED, align = "center"),
                label("stepLabel", "STEPS", 138, 160, 60, align = "center"),
                FaceElement("steps", "number", 138, 176, 60, bind = Binding.STEPS.key, font = "space-grotesk", size = 22f, weight = 700, align = "center"),
            ) + hands("bar", "needle", minuteWidth = 9f),
        ),
    )

    val paper = Preset(
        "paper", "Paper", "Light mode: off-white dial, black batons and hands, red seconds — like a printed manual.",
        FaceSpec(
            name = "Paper",
            background = Background("#EDEDED"),
            elements = ticks(color = INK, minutes = "#9A9A9A") + listOf(
                FaceElement("brand", "text", 0, 82, W, text = "SILENTFIT", font = "space-mono", size = 13f, weight = 700, align = "center", color = "#555555", tracking = 0.18f),
                FaceElement("dayBox", "shape", 160, 126, 44, 30, shape = "rect", size = 4f, stroke = 3f, fill = false, color = INK),
                FaceElement("day", "number", 160, 128, 44, bind = Binding.DAY.key, font = "space-grotesk", size = 20f, weight = 700, color = INK, align = "center"),
            ) + hands("bar", color = INK, cap = INK),
        ),
    )

    val subdials = Preset(
        "subdials", "Subdials", "Two black subdials on a light dial with centred step and calorie counts, after the official sport face.",
        FaceSpec(
            name = "Subdials",
            background = Background("#E6E4DE"),
            elements = ticks(color = INK, minutes = "#B0AEA8") + listOf(
                FaceElement("band", "shape", 18, 92, 204, 96, shape = "rect", size = 48f, color = "#C4C3BC"),
                FaceElement("dialL", "shape", 22, 96, 88, 88, shape = "circle", color = "#000000"),
                FaceElement("dialR", "shape", 130, 96, 88, 88, shape = "circle", color = "#000000"),
                FaceElement("ringL", "shape", 26, 100, 80, 80, shape = "ticks", count = 30, size = 6f, stroke = 2f, color = "#8A8A8A"),
                FaceElement("ringR", "shape", 134, 100, 80, 80, shape = "ticks", count = 30, size = 6f, stroke = 2f, color = "#8A8A8A"),
                label("stepsLabel", "STEPS", 22, 116, 88, color = GREY, align = "center"),
                FaceElement("steps", "number", 22, 136, 88, bind = Binding.STEPS.key, font = "space-grotesk", size = 20f, weight = 700, color = WHITE, align = "center"),
                label("kcalLabel", "KCAL", 130, 116, 88, color = GREY, align = "center"),
                FaceElement("kcal", "number", 130, 136, 88, bind = Binding.CALORIES.key, font = "space-grotesk", size = 20f, weight = 700, color = WHITE, align = "center"),
                FaceElement("weekday", "sprite", 0, 52, W, bind = Binding.WEEKDAY.key, font = "space-mono", size = 18f, weight = 700, color = INK, align = "center"),
                FaceElement("day", "number", 0, 204, W, bind = Binding.DAY.key, font = "space-grotesk", size = 22f, weight = 700, color = INK, align = "center"),
            ) + hands("bar", color = INK, cap = INK),
        ),
    )

    val glyphRings = Preset(
        "glyph", "Glyph Rings", "Thick segmented arcs like Nothing's Glyph lights around bold line hands.",
        FaceSpec(
            name = "Glyph Rings",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("outer", "shape", 8, CY - 112, 224, 224, shape = "arc", startAngle = 20f, sweep = 140f, stroke = 8f, color = WHITE),
                FaceElement("outer2", "shape", 8, CY - 112, 224, 224, shape = "arc", startAngle = 200f, sweep = 140f, stroke = 8f, color = DIM),
                FaceElement("inner", "shape", 36, CY - 84, 168, 168, shape = "arc", startAngle = 300f, sweep = 100f, stroke = 6f, color = RED),
                label("stepsLabel", "STEPS", 0, 178, W, align = "center"),
                FaceElement("steps", "number", 0, 194, W, bind = Binding.STEPS.key, font = "space-grotesk", size = 24f, weight = 700, align = "center"),
            ) + hands("line", hourWidth = 8f, minuteWidth = 6f),
        ),
    )

    val minimal = Preset(
        "minimal", "Minimal", "Just thick hands, a red seconds hand and four markers. The most readable face.",
        FaceSpec(
            name = "Minimal",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("m12", "shape", CX - 4, 24, 8, 26, shape = "rect", size = 3f, color = RED),
                FaceElement("m6", "shape", CX - 4, 230, 8, 26, shape = "rect", size = 3f, color = WHITE),
                FaceElement("m9", "shape", 10, CY - 4, 26, 8, shape = "rect", size = 3f, color = WHITE),
                FaceElement("m3", "shape", 204, CY - 4, 26, 8, shape = "rect", size = 3f, color = WHITE),
            ) + hands("bar", hourWidth = 12f, minuteWidth = 9f, cap = WHITE),
        ),
    )

    // ------------------------------------------------------------------ digital

    private fun colon(y: Int, size: Int = 8, color: String = RED, gap: Int = 22) = listOf(
        FaceElement("colon1", "shape", CX - size / 2, y, size, size, shape = "circle", color = color),
        FaceElement("colon2", "shape", CX - size / 2, y + gap, size, size, shape = "circle", color = color),
    )

    val dotMatrixDigital = Preset(
        "dot-matrix-digital", "Dot Matrix Digital", "Large Doto hours over minutes on a dotted field, date and heart rate.",
        FaceSpec(
            name = "Dot Matrix Digital",
            background = Background("#000000", pattern = "dots", patternColor = "#1E1E1E", patternSpacing = 8),
            elements = listOf(
                FaceElement("hour", "number", 0, 26, W, bind = Binding.HOUR.key, font = "doto", size = 70f, weight = 700, align = "center", color = WHITE),
                FaceElement("minute", "number", 0, 120, W, bind = Binding.MINUTE.key, font = "doto", size = 70f, weight = 700, align = "center", color = GREY),
                FaceElement("weekday", "sprite", 30, 230, 64, bind = Binding.WEEKDAY.key, font = "space-mono", size = 18f, weight = 700, color = GREY, align = "right"),
                FaceElement("day", "number", 102, 228, bind = Binding.DAY.key, font = "doto", size = 22f, weight = 700, color = WHITE),
                FaceElement("dot", "shape", 146, 238, 8, 8, shape = "circle", color = RED),
                FaceElement("hr", "number", 160, 228, bind = Binding.HEART_RATE.key, font = "doto", size = 22f, weight = 700, color = RED),
            ),
        ),
    )

    val ledMatrix = Preset(
        "led-matrix", "LED Matrix", "5×7 LED digits with the unlit dots showing, like Nothing's Glyph matrix.",
        FaceSpec(
            name = "LED Matrix",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("weekday", "sprite", 0, 34, 112, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 21f, color = GREY, align = "right"),
                FaceElement("day", "number", 128, 34, bind = Binding.DAY.key, glyph = "dot-matrix", size = 21f, color = WHITE),
                FaceElement("hour", "number", 0, 92, 106, bind = Binding.HOUR.key, glyph = "dot-matrix", ghost = GHOST, size = 56f, color = WHITE, align = "right"),
                FaceElement("minute", "number", 134, 92, bind = Binding.MINUTE.key, glyph = "dot-matrix", ghost = GHOST, size = 56f, color = WHITE),
            ) + colon(108) + listOf(
                label("bpmLabel", "BPM", 30, 186),
                FaceElement("hr", "number", 30, 206, bind = Binding.HEART_RATE.key, glyph = "dot-matrix", size = 28f, color = RED),
                label("batLabel", "BAT", 130, 186, 80, align = "right"),
                FaceElement("battery", "number", 130, 206, 80, bind = Binding.BATTERY.key, glyph = "dot-matrix", size = 28f, color = WHITE, align = "right"),
            ),
        ),
    )

    val segment = Preset(
        "segment", "Segment", "Red seven-segment time over ghost segments, steps, date and battery.",
        FaceSpec(
            name = "Segment",
            background = Background("#000000"),
            elements = listOf(
                label("stepsLabel", "STEPS", 24, 30),
                FaceElement("steps", "number", 24, 50, bind = Binding.STEPS.key, glyph = "segment", ghost = GHOST, size = 26f, color = WHITE),
                FaceElement("weekday", "sprite", 140, 30, 76, bind = Binding.WEEKDAY.key, font = "space-mono", size = 14f, weight = 700, color = GREY, align = "right"),
                FaceElement("day", "number", 156, 50, 60, bind = Binding.DAY.key, glyph = "segment", ghost = GHOST, size = 26f, color = WHITE, align = "right"),
                FaceElement("hour", "number", 0, 104, 112, bind = Binding.HOUR.key, glyph = "segment", ghost = GHOST, size = 64f, color = RED, align = "right"),
                FaceElement("minute", "number", 128, 104, 112, bind = Binding.MINUTE.key, glyph = "segment", ghost = GHOST, size = 64f, color = RED),
            ) + colon(120, size = 8, gap = 26) + listOf(
                FaceElement("bar", "shape", 24, 196, 192, 8, shape = "segments", count = 16, sweep = 360f, color = DIM),
                label("batLabel", "BATTERY", 24, 220),
                FaceElement("battery", "number", 136, 216, 80, bind = Binding.BATTERY.key, glyph = "segment", size = 26f, color = WHITE, align = "right"),
            ),
        ),
    )

    val stack = Preset(
        "stack", "Stack", "Bold Grotesk hours over red minutes, a vertical weekday on the edge.",
        FaceSpec(
            name = "Stack",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("hour", "number", 0, 30, W, bind = Binding.HOUR.key, font = "space-grotesk", size = 66f, weight = 700, align = "center", color = WHITE),
                FaceElement("minute", "number", 0, 118, W, bind = Binding.MINUTE.key, font = "space-grotesk", size = 66f, weight = 700, align = "center", color = RED),
                FaceElement("weekday", "sprite", 8, 96, bind = Binding.WEEKDAY.key, font = "space-mono", size = 18f, weight = 700, color = GREY, rotation = 270),
                FaceElement("day", "number", 196, 126, bind = Binding.DAY.key, glyph = "dot-matrix", size = 21f, color = WHITE),
                FaceElement("rule", "shape", 60, 222, 120, 3, shape = "rect", size = 0f, color = DIM),
                FaceElement("brand", "text", 0, 234, W, text = "SILENTFIT", font = "space-mono", size = 13f, weight = 700, align = "center", color = GREY, tracking = 0.2f),
            ),
        ),
    )

    val glyphData = Preset(
        "glyph-data", "Glyph Data", "Compact time above steps, calories, heart rate and battery rows, like Nothing widgets.",
        FaceSpec(
            name = "Glyph Data",
            background = Background("#000000"),
            elements = buildList {
                add(FaceElement("hour", "number", 18, 22, bind = Binding.HOUR.key, font = "doto", size = 52f, weight = 700, color = WHITE))
                add(FaceElement("sep", "shape", 94, 40, 7, 7, shape = "circle", color = RED))
                add(FaceElement("sep2", "shape", 94, 60, 7, 7, shape = "circle", color = RED))
                add(FaceElement("minute", "number", 108, 22, bind = Binding.MINUTE.key, font = "doto", size = 52f, weight = 700, color = WHITE))
                add(FaceElement("weekday", "sprite", 176, 26, 48, bind = Binding.WEEKDAY.key, font = "space-mono", size = 14f, weight = 700, color = GREY, align = "right"))
                add(FaceElement("day", "number", 184, 46, 40, bind = Binding.DAY.key, font = "space-grotesk", size = 20f, weight = 700, color = WHITE, align = "right"))
                val rows = listOf("STEPS" to Binding.STEPS, "KCAL" to Binding.CALORIES, "BPM" to Binding.HEART_RATE, "BATT" to Binding.BATTERY)
                rows.forEachIndexed { i, (text, b) ->
                    val y = 104 + i * 40
                    add(label("l$i", text, 18, y + 6))
                    add(FaceElement("v$i", "number", 96, y, 128, bind = b.key, glyph = "dot-matrix", size = 21f, color = if (b == Binding.HEART_RATE) RED else WHITE, align = "right"))
                    add(FaceElement("d$i", "shape", 18, y + 30, 206, 2, shape = "rect", size = 0f, color = "#222222"))
                }
            },
        ),
    )

    val all = listOf(dotMatrix, instrument, paper, subdials, glyphRings, minimal, dotMatrixDigital, ledMatrix, segment, stack, glyphData)
}
