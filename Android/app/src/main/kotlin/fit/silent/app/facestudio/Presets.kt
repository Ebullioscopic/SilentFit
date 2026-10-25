package fit.silent.app.facestudio

import fit.silent.core.watchface.Background
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceSpec

data class Preset(val key: String, val title: String, val description: String, val spec: FaceSpec, val experimental: Boolean = false)

/** Built-in designs. Coordinates are for the NF 2's 240×280 screen. */
object Presets {
    private const val W = 240
    private const val H = 280
    private const val CX = W / 2
    private const val CY = H / 2
    private const val RED = "#D71921"
    private const val WHITE = "#FFFFFF"
    private const val GREY = "#999999"
    private const val DIM = "#333333"

    private fun hands(style: String, minuteStyle: String = style, color: String = WHITE, accent: String = RED, width: Float = 6f) = listOf(
        FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key, handStyle = style, color = color, length = 62, tail = 10, stroke = width, pivotX = CX, pivotY = CY, size = 0f),
        FaceElement("minute", "hand", bind = Binding.MINUTE_HAND.key, handStyle = minuteStyle, color = color, length = 96, tail = 12, stroke = width * 0.75f, pivotX = CX, pivotY = CY, size = 0f),
        FaceElement("pin", "shape", CX - 4, CY - 4, 8, 8, shape = "circle", color = accent),
    )

    val dotMatrix = Preset(
        "dot-matrix", "Dot Matrix", "Nothing's signature dotted dial with dot hands. Uses only verified watch data.",
        FaceSpec(
            name = "Dot Matrix",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("ring", "shape", 10, 30, 220, 220, shape = "dot-ring", count = 60, stroke = 1.6f, color = "#666666"),
                FaceElement("twelve", "text", 0, 44, W, 0, text = "12", font = "doto", size = 26f, weight = 700, align = "center", color = WHITE),
                FaceElement("label", "text", 0, 196, W, 0, text = "silentfit", font = "space-mono", size = 10f, align = "center", color = GREY, uppercase = true, tracking = 0.12f),
                FaceElement("weekday", "sprite", 74, 214, bind = Binding.WEEKDAY.key, font = "space-mono", size = 13f, color = GREY),
                FaceElement("day", "number", 112, 211, bind = Binding.DAY.key, font = "doto", size = 18f, weight = 700, color = WHITE),
                FaceElement("dot", "shape", 152, 216, 6, 6, shape = "circle", color = RED),
                FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key, handStyle = "dots", color = WHITE, length = 58, stroke = 5f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("minute", "hand", bind = Binding.MINUTE_HAND.key, handStyle = "dots", color = WHITE, length = 92, stroke = 4f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("pin", "shape", CX - 4, CY - 4, 8, 8, shape = "circle", color = RED),
            ),
        ),
    )

    val instrument = Preset(
        "instrument", "Instrument", "Thin tick ring, needle hands and stat readouts — an instrument panel on the wrist.",
        FaceSpec(
            name = "Instrument",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("ticks", "shape", 6, 26, 228, 228, shape = "ticks", count = 60, size = 6f, stroke = 1f, color = "#666666"),
                FaceElement("n12", "text", 0, 46, W, 0, text = "12", font = "space-mono", size = 16f, align = "center"),
                FaceElement("n3", "text", 196, 130, text = "3", font = "space-mono", size = 16f),
                FaceElement("n9", "text", 34, 130, text = "9", font = "space-mono", size = 16f),
                FaceElement("hrLabel", "text", 64, 176, text = "BPM", font = "space-mono", size = 9f, color = GREY, tracking = 0.1f),
                FaceElement("hr", "number", 62, 188, bind = Binding.HEART_RATE.key, font = "space-mono", size = 16f, color = RED),
                FaceElement("stepLabel", "text", 136, 176, text = "STEPS", font = "space-mono", size = 9f, color = GREY, tracking = 0.1f),
                FaceElement("steps", "number", 134, 188, bind = Binding.STEPS.key, font = "space-mono", size = 16f),
            ) + hands("bar", "needle", width = 7f),
        ),
    )

    val paper = Preset(
        "paper", "Paper", "Light mode: printed-manual off-white with black ink hands.",
        FaceSpec(
            name = "Paper",
            background = Background("#F5F5F5"),
            elements = listOf(
                FaceElement("ticks", "shape", 10, 30, 220, 220, shape = "ticks", count = 12, size = 10f, stroke = 2f, color = "#1A1A1A"),
                FaceElement("brand", "text", 0, 92, W, 0, text = "SILENTFIT", font = "space-mono", size = 9f, align = "center", color = "#666666", tracking = 0.2f),
                FaceElement("day", "number", 150, 132, bind = Binding.DAY.key, font = "space-mono", size = 14f, color = "#1A1A1A"),
            ) + hands("bar", color = "#000000", width = 6f),
        ),
    )

    val glyphRings = Preset(
        "glyph", "Glyph Rings", "Concentric segmented rings framing simple line hands.",
        FaceSpec(
            name = "Glyph Rings",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("outer", "shape", 8, 28, 224, 224, shape = "arc", startAngle = 20f, sweep = 140f, stroke = 3f, color = WHITE),
                FaceElement("outer2", "shape", 8, 28, 224, 224, shape = "arc", startAngle = 200f, sweep = 140f, stroke = 3f, color = DIM),
                FaceElement("inner", "shape", 40, 60, 160, 160, shape = "arc", startAngle = 300f, sweep = 100f, stroke = 2f, color = RED),
                FaceElement("steps", "number", 92, 186, bind = Binding.STEPS.key, font = "space-mono", size = 14f),
                FaceElement("stepsLabel", "text", 0, 206, W, 0, text = "STEPS", font = "space-mono", size = 9f, color = GREY, align = "center"),
            ) + hands("line", width = 3f),
        ),
    )

    val dotMatrixDigital = Preset(
        "dot-matrix-digital", "Dot Matrix Digital", "Large Doto hours over minutes on a dotted field, with date and heart rate.",
        FaceSpec(
            name = "Dot Matrix Digital",
            background = Background("#000000", pattern = "dots", patternColor = "#1A1A1A", patternSpacing = 8),
            elements = listOf(
                FaceElement("hour", "number", 0, 30, W, bind = Binding.HOUR.key, font = "doto", size = 70f, weight = 700, align = "center", color = WHITE),
                FaceElement("minute", "number", 0, 126, W, bind = Binding.MINUTE.key, font = "doto", size = 70f, weight = 700, align = "center", color = GREY),
                FaceElement("weekday", "sprite", 54, 236, bind = Binding.WEEKDAY.key, font = "space-mono", size = 13f, color = GREY),
                FaceElement("day", "number", 100, 233, bind = Binding.DAY.key, font = "doto", size = 18f, weight = 700, color = WHITE),
                FaceElement("dot", "shape", 134, 240, 6, 6, shape = "circle", color = RED),
                FaceElement("hr", "number", 146, 233, bind = Binding.HEART_RATE.key, font = "doto", size = 18f, weight = 700, color = RED),
            ),
        ),
    )

    private const val GHOST = "#1C1C1C"

    private fun colon(y: Int, size: Int = 6, color: String = RED, gap: Int = 22) = listOf(
        FaceElement("colon1", "shape", CX - size / 2, y, size, size, shape = "circle", color = color),
        FaceElement("colon2", "shape", CX - size / 2, y + gap, size, size, shape = "circle", color = color),
    )

    val ledMatrix = Preset(
        "led-matrix", "LED Matrix", "5×7 LED digits with unlit dots showing, like Nothing's Glyph matrix.",
        FaceSpec(
            name = "LED Matrix",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("weekday", "sprite", 0, 40, 120, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 14f, color = GREY, align = "right"),
                FaceElement("day", "number", 132, 40, bind = Binding.DAY.key, glyph = "dot-matrix", size = 14f, color = WHITE),
                FaceElement("hour", "number", 0, 96, 106, bind = Binding.HOUR.key, glyph = "dot-matrix", ghost = GHOST, size = 56f, color = WHITE, align = "right"),
                FaceElement("minute", "number", 134, 96, bind = Binding.MINUTE.key, glyph = "dot-matrix", ghost = GHOST, size = 56f, color = WHITE),
            ) + colon(112) + listOf(
                FaceElement("bpmLabel", "text", 40, 196, text = "BPM", font = "space-mono", size = 9f, color = GREY, tracking = 0.1f),
                FaceElement("hr", "number", 40, 212, bind = Binding.HEART_RATE.key, glyph = "dot-matrix", size = 14f, color = RED),
                FaceElement("batLabel", "text", 0, 196, 200, text = "BAT", font = "space-mono", size = 9f, color = GREY, tracking = 0.1f, align = "right"),
                FaceElement("battery", "number", 140, 212, 60, bind = Binding.BATTERY.key, glyph = "dot-matrix", size = 14f, color = WHITE, align = "right"),
            ),
        ),
    )

    val segment = Preset(
        "segment", "Segment", "Red seven-segment time over ghost segments, with steps and battery readouts.",
        FaceSpec(
            name = "Segment",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("stepsLabel", "text", 30, 44, text = "STEPS", font = "space-mono", size = 9f, color = GREY, tracking = 0.1f),
                FaceElement("steps", "number", 30, 58, bind = Binding.STEPS.key, glyph = "segment", ghost = GHOST, size = 20f, color = WHITE),
                FaceElement("weekday", "sprite", 150, 44, 60, bind = Binding.WEEKDAY.key, font = "space-mono", size = 10f, color = GREY, align = "right"),
                FaceElement("day", "number", 170, 58, 40, bind = Binding.DAY.key, glyph = "segment", ghost = GHOST, size = 20f, color = WHITE, align = "right"),
                FaceElement("hour", "number", 0, 104, 114, bind = Binding.HOUR.key, glyph = "segment", ghost = GHOST, size = 64f, color = RED, align = "right"),
                FaceElement("minute", "number", 126, 104, 114, bind = Binding.MINUTE.key, glyph = "segment", ghost = GHOST, size = 64f, color = RED),
            ) + colon(120, size = 7, color = RED, gap = 26) + listOf(
                FaceElement("bar", "shape", 30, 206, 180, 6, shape = "segments", count = 24, sweep = 360f, color = "#333333"),
                FaceElement("batLabel", "text", 30, 222, text = "BATTERY", font = "space-mono", size = 9f, color = GREY, tracking = 0.1f),
                FaceElement("battery", "number", 150, 220, 60, bind = Binding.BATTERY.key, glyph = "segment", size = 16f, color = WHITE, align = "right"),
            ),
        ),
    )

    val stack = Preset(
        "stack", "Stack", "Hours over minutes in light Grotesk, a vertical weekday on the edge and a red minute line.",
        FaceSpec(
            name = "Stack",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("hour", "number", 0, 30, W, bind = Binding.HOUR.key, font = "space-grotesk", size = 64f, weight = 300, align = "center", color = WHITE),
                FaceElement("minute", "number", 0, 116, W, bind = Binding.MINUTE.key, font = "space-grotesk", size = 64f, weight = 300, align = "center", color = RED),
                FaceElement("weekday", "sprite", 14, 104, bind = Binding.WEEKDAY.key, font = "space-mono", size = 12f, color = GREY, rotation = 270),
                FaceElement("day", "number", 206, 128, bind = Binding.DAY.key, glyph = "dot-matrix", size = 14f, color = WHITE),
                FaceElement("rule", "shape", 72, 226, 96, 1, shape = "rect", size = 0f, color = "#333333"),
                FaceElement("brand", "text", 0, 236, W, text = "SILENTFIT", font = "space-mono", size = 9f, align = "center", color = GREY, tracking = 0.2f),
            ),
        ),
    )

    val glyphData = Preset(
        "glyph-data", "Glyph Data", "A Nothing widget stack: compact time, then steps, calories, heart rate and battery rows.",
        FaceSpec(
            name = "Glyph Data",
            background = Background("#000000"),
            elements = buildList {
                add(FaceElement("hour", "number", 24, 30, bind = Binding.HOUR.key, font = "doto", size = 40f, weight = 700, color = WHITE))
                add(FaceElement("sep", "shape", 86, 44, 5, 5, shape = "circle", color = RED))
                add(FaceElement("sep2", "shape", 86, 60, 5, 5, shape = "circle", color = RED))
                add(FaceElement("minute", "number", 98, 30, bind = Binding.MINUTE.key, font = "doto", size = 40f, weight = 700, color = WHITE))
                add(FaceElement("weekday", "sprite", 156, 34, 60, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 12f, color = GREY, align = "right"))
                add(FaceElement("day", "number", 176, 52, 40, bind = Binding.DAY.key, glyph = "dot-matrix", size = 12f, color = WHITE, align = "right"))
                val rows = listOf("STEPS" to Binding.STEPS, "KCAL" to Binding.CALORIES, "BPM" to Binding.HEART_RATE, "BATT" to Binding.BATTERY)
                rows.forEachIndexed { i, (label, b) ->
                    val y = 104 + i * 36
                    add(FaceElement("l$i", "text", 24, y + 3, text = label, font = "space-mono", size = 10f, color = GREY, tracking = 0.1f))
                    add(FaceElement("v$i", "number", 96, y, 120, bind = b.key, glyph = "dot-matrix", size = 14f, color = if (b == Binding.HEART_RATE) RED else WHITE, align = "right"))
                    add(FaceElement("d$i", "shape", 24, y + 24, 192, 3, shape = "dot-matrix", stroke = 0.6f, color = "#262626"))
                }
            },
        ),
    )

    val all = listOf(dotMatrix, dotMatrixDigital, ledMatrix, segment, stack, glyphData, instrument, paper, glyphRings)
}
