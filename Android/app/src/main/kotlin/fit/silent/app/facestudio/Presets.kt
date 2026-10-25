package fit.silent.app.facestudio

import fit.silent.core.watchface.Background
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceSpec

data class Preset(val key: String, val title: String, val description: String, val spec: FaceSpec, val experimental: Boolean = false) {
    val isAnalogue get() = spec.elements.any { it.type == "hand" }
}

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

    // ------------------------------------------------------------------ minimal & dot cloud

    private fun cloud(x: Int, y: Int, size: Int, color: String = "#5A5A5A", spacing: Float = 12f, dot: Float = 5f) =
        FaceElement("cloud", "shape", x, y, size, size, shape = "halftone", size = spacing, stroke = dot, color = color)

    val cloud = Preset(
        "cloud", "Cloud", "An off-centre halftone dot cloud behind bold hands, with the date in the corner.",
        FaceSpec(
            name = "Cloud",
            background = Background("#000000"),
            elements = listOf(
                cloud(84, -46, 260),
                FaceElement("twelve", "shape", CX - 5, 20, 10, 10, shape = "circle", color = RED),
                FaceElement("weekday", "sprite", 18, 204, 72, bind = Binding.WEEKDAY.key, font = "space-mono", size = 18f, weight = 700, color = GREY, align = "center"),
                FaceElement("day", "number", 18, 226, 72, bind = Binding.DAY.key, font = "doto", size = 30f, weight = 700, color = WHITE, align = "center"),
            ) + hands("bar", hourWidth = 12f, minuteWidth = 9f, cap = WHITE),
        ),
    )

    val cloudDigital = Preset(
        "cloud-digital", "Cloud Digital", "Right-aligned bold time with a halftone cloud rising from the corner.",
        FaceSpec(
            name = "Cloud Digital",
            background = Background("#000000"),
            elements = listOf(
                cloud(-90, 120, 250, color = "#4A4A4A"),
                FaceElement("weekday", "sprite", 18, 30, 64, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 21f, color = GREY, align = "center"),
                FaceElement("day", "number", 18, 58, 64, bind = Binding.DAY.key, glyph = "dot-matrix", size = 21f, color = WHITE, align = "center"),
                FaceElement("hour", "number", 96, 26, 124, bind = Binding.HOUR.key, font = "space-grotesk", size = 66f, weight = 700, align = "right", color = WHITE),
                FaceElement("minute", "number", 96, 110, 124, bind = Binding.MINUTE.key, font = "space-grotesk", size = 66f, weight = 700, align = "right", color = RED),
                FaceElement("battery", "number", 160, 228, 60, bind = Binding.BATTERY.key, glyph = "dot-matrix", size = 21f, color = WHITE, align = "right"),
            ),
        ),
    )

    val twoHands = Preset(
        "two-hands", "Two Hands", "Nothing but two thick hands and a red dot at twelve.",
        FaceSpec(
            name = "Two Hands",
            background = Background("#000000"),
            elements = listOf(FaceElement("twelve", "shape", CX - 6, 18, 12, 12, shape = "circle", color = RED)) +
                hands("bar", hourWidth = 14f, minuteWidth = 10f, seconds = false, cap = WHITE),
        ),
    )

    val indexDots = Preset(
        "index-dots", "Index Dots", "Twelve big dots and dotted hands, with a red seconds hand.",
        FaceSpec(
            name = "Index Dots",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("dots", "shape", 10, CY - 110, 220, 220, shape = "dot-ring", count = 12, stroke = 5f, color = WHITE),
                FaceElement("twelve", "shape", CX - 7, CY - 110 + 1, 14, 14, shape = "circle", color = RED),
            ) + hands("dots", hourWidth = 10f, minuteWidth = 8f, cap = RED),
        ),
    )

    val monoLed = Preset(
        "mono-led", "Mono LED", "Huge stacked LED hours and minutes with the date beside them.",
        FaceSpec(
            name = "Mono LED",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("hour", "number", 20, 30, bind = Binding.HOUR.key, glyph = "dot-matrix", ghost = GHOST, size = 70f, color = WHITE),
                FaceElement("minute", "number", 20, 120, bind = Binding.MINUTE.key, glyph = "dot-matrix", ghost = GHOST, size = 70f, color = RED),
                FaceElement("weekday", "sprite", 146, 40, 80, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 21f, color = GREY, align = "center"),
                FaceElement("day", "number", 146, 70, 80, bind = Binding.DAY.key, glyph = "dot-matrix", size = 28f, color = WHITE, align = "center"),
                label("batLabel", "BAT", 146, 214, 80, align = "center"),
                FaceElement("battery", "number", 146, 232, 80, bind = Binding.BATTERY.key, glyph = "dot-matrix", size = 21f, color = WHITE, align = "center"),
            ),
        ),
    )

    // ------------------------------------------------------------------ grids, instruments & more

    /** Swiss-style quadrant grid: hours | minutes over two data columns. */
    private fun swiss(key: String, title: String, dark: Boolean): Preset {
        val ink = if (dark) WHITE else INK
        val rule = if (dark) DIM else "#1A1A1A"
        val sub = if (dark) GREY else "#555555"
        fun value(id: String, b: Binding, x: Int, y: Int, color: String = ink) =
            FaceElement(id, "number", x, y, 120, bind = b.key, font = "space-grotesk", size = 24f, weight = 700, color = color, align = "center")
        return Preset(
            key, title, "A Swiss poster grid: bold hours and red minutes over steps, heart rate, date and battery.",
            FaceSpec(
                name = title,
                background = Background(if (dark) "#000000" else "#F2F2F2"),
                elements = listOf(
                    FaceElement("vRule", "shape", CX - 1, 18, 3, 244, shape = "rect", size = 0f, color = rule),
                    FaceElement("hRule", "shape", 12, CY - 1, 216, 3, shape = "rect", size = 0f, color = rule),
                    FaceElement("mark", "shape", 14, 18, 12, 12, shape = "rect", size = 0f, color = RED),
                    FaceElement("hour", "number", 0, 36, 120, bind = Binding.HOUR.key, font = "space-grotesk", size = 66f, weight = 700, align = "center", color = ink),
                    FaceElement("minute", "number", 120, 36, 120, bind = Binding.MINUTE.key, font = "space-grotesk", size = 66f, weight = 700, align = "center", color = RED),
                    label("stepsLabel", "STEPS", 0, 154, 120, color = sub, align = "center"),
                    value("steps", Binding.STEPS, 0, 172),
                    label("bpmLabel", "BPM", 0, 212, 120, color = sub, align = "center"),
                    value("hr", Binding.HEART_RATE, 0, 230, RED),
                    FaceElement("weekday", "sprite", 120, 154, 120, bind = Binding.WEEKDAY.key, font = "space-mono", size = 14f, weight = 700, color = sub, align = "center"),
                    value("day", Binding.DAY, 120, 172),
                    label("batLabel", "BATTERY", 120, 212, 120, color = sub, align = "center"),
                    value("battery", Binding.BATTERY, 120, 230),
                ),
            ),
        )
    }

    val swissGrid = swiss("swiss-grid", "Swiss Grid", dark = false)
    val swissDark = swiss("swiss-dark", "Swiss Dark", dark = true)

    /** Twelve hour numerals on a circle of [radius] around the pivot. */
    private fun numerals(radius: Int, size: Float, color: String, font: String = "space-grotesk") = (1..12).map { h ->
        val a = Math.toRadians(h * 30.0)
        val x = CX + (radius * kotlin.math.sin(a)).toInt()
        val y = CY - (radius * kotlin.math.cos(a)).toInt() - (size * 0.62f).toInt()
        FaceElement("n$h", "text", x, y, text = "$h", font = font, size = size, weight = 700, align = "center", color = color)
    }

    val braun = Preset(
        "braun", "Braun", "Industrial-design dial: twelve bold numerals, white batons, red seconds on graphite.",
        FaceSpec(
            name = "Braun",
            background = Background("#1C1C1C"),
            elements = listOf(
                FaceElement("minutes", "shape", 6, CY - 114, 228, 228, shape = "ticks", count = 60, size = 6f, stroke = 2f, color = "#555555"),
            ) + numerals(88, 22f, WHITE) + hands("bar", hourWidth = 11f, minuteWidth = 8f, cap = WHITE),
        ),
    )

    val railway = Preset(
        "railway", "Railway", "Station-clock clarity: black batons on white, thick hands, red seconds.",
        FaceSpec(
            name = "Railway",
            background = Background("#F5F5F5"),
            elements = ticks(color = INK, minutes = INK) + hands("bar", color = INK, hourWidth = 13f, minuteWidth = 10f, cap = RED),
        ),
    )

    val radar = Preset(
        "radar", "Radar", "Concentric rings and a crosshair behind needle hands, step count below.",
        FaceSpec(
            name = "Radar",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("r1", "shape", 10, CY - 110, 220, 220, shape = "ring", stroke = 3f, color = DIM),
                FaceElement("r2", "shape", 45, CY - 75, 150, 150, shape = "ring", stroke = 3f, color = DIM),
                FaceElement("r3", "shape", 80, CY - 40, 80, 80, shape = "ring", stroke = 3f, color = DIM),
                FaceElement("xh", "shape", 10, CY - 1, 220, 3, shape = "rect", size = 0f, color = DIM),
                FaceElement("xv", "shape", CX - 1, 30, 3, 220, shape = "rect", size = 0f, color = DIM),
                FaceElement("blip", "shape", 160, 84, 10, 10, shape = "circle", color = RED),
                FaceElement("steps", "number", 0, 252, W, bind = Binding.STEPS.key, font = "space-grotesk", size = 20f, weight = 700, color = WHITE, align = "center"),
            ) + hands("needle", hourWidth = 12f, minuteWidth = 10f),
        ),
    )

    val paperCloud = Preset(
        "paper-cloud", "Paper Cloud", "Light dial with a soft halftone cloud, black batons and hands, date bottom right.",
        FaceSpec(
            name = "Paper Cloud",
            background = Background("#EDEDED"),
            elements = listOf(cloud(-90, 120, 260, color = "#BDBDBD")) + ticks(color = INK, minutes = null) + listOf(
                FaceElement("weekday", "sprite", 140, 196, 70, bind = Binding.WEEKDAY.key, font = "space-mono", size = 14f, weight = 700, color = "#555555", align = "center"),
                FaceElement("day", "number", 140, 216, 70, bind = Binding.DAY.key, font = "space-grotesk", size = 24f, weight = 700, color = INK, align = "center"),
            ) + hands("bar", color = INK, cap = INK),
        ),
    )

    val bigDate = Preset(
        "big-date", "Big Date", "The date as the hero in Doto, with LED time underneath.",
        FaceSpec(
            name = "Big Date",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("weekday", "sprite", 0, 30, W, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 21f, color = RED, align = "center"),
                FaceElement("day", "number", 0, 64, W, bind = Binding.DAY.key, font = "doto", size = 70f, weight = 700, color = WHITE, align = "center"),
                FaceElement("rule", "shape", 60, 176, 120, 3, shape = "rect", size = 0f, color = DIM),
                FaceElement("hour", "number", 0, 200, 112, bind = Binding.HOUR.key, glyph = "dot-matrix", ghost = GHOST, size = 28f, color = WHITE, align = "right"),
                FaceElement("minute", "number", 128, 200, bind = Binding.MINUTE.key, glyph = "dot-matrix", ghost = GHOST, size = 28f, color = WHITE),
            ) + colon(204, size = 6, color = GREY, gap = 14),
        ),
    )

    val terminal = Preset(
        "terminal", "Terminal", "A monospace console: time with a red cursor, then steps, heart rate and battery readouts.",
        FaceSpec(
            name = "Terminal",
            background = Background("#000000"),
            elements = buildList {
                add(label("prompt", "SILENTFIT://", 18, 22))
                add(FaceElement("hour", "number", 18, 44, bind = Binding.HOUR.key, font = "space-mono", size = 52f, weight = 700, color = WHITE))
                add(FaceElement("sep", "text", 82, 44, text = ":", font = "space-mono", size = 52f, weight = 700, color = GREY))
                add(FaceElement("minute", "number", 112, 44, bind = Binding.MINUTE.key, font = "space-mono", size = 52f, weight = 700, color = WHITE))
                add(FaceElement("cursor", "shape", 182, 58, 16, 48, shape = "rect", size = 0f, color = RED))
                listOf("> STEPS" to Binding.STEPS, "> BPM" to Binding.HEART_RATE, "> BATT" to Binding.BATTERY).forEachIndexed { i, (text, b) ->
                    val y = 140 + i * 40
                    add(label("l$i", text, 18, y + 4))
                    add(FaceElement("v$i", "number", 110, y, 112, bind = b.key, font = "space-mono", size = 21f, weight = 700, color = if (b == Binding.HEART_RATE) RED else WHITE, align = "right"))
                }
            },
        ),
    )

    // ------------------------------------------------------------------ racing, futuristic, minimal combinations

    private fun value(id: String, b: Binding, x: Int, y: Int, w: Int, color: String = WHITE, size: Float = 24f, align: String = "center") =
        FaceElement(id, "number", x, y, w, bind = b.key, font = "space-grotesk", size = size, weight = 700, color = color, align = align)

    val f1Telemetry = Preset(
        "f1-telemetry", "F1 Telemetry", "Timing-tower graphics: kerb stripes, bold lap-time digits, rev bar and telemetry readouts.",
        FaceSpec(
            name = "F1 Telemetry",
            background = Background("#000000"),
            elements = buildList {
                repeat(12) { i -> add(FaceElement("kerb$i", "shape", i * 20, 14, 20, 8, shape = "rect", size = 0f, color = if (i % 2 == 0) RED else WHITE)) }
                add(label("lapLabel", "LAP TIME", 18, 36))
                add(FaceElement("pBox", "shape", 186, 34, 38, 24, shape = "rect", size = 3f, color = RED))
                add(FaceElement("pText", "text", 186, 36, 38, text = "P1", font = "space-grotesk", size = 17f, weight = 700, align = "center", color = WHITE))
                add(FaceElement("hour", "number", 14, 58, 96, bind = Binding.HOUR.key, font = "space-grotesk", size = 66f, weight = 700, align = "right", color = WHITE))
                add(FaceElement("c1", "shape", 114, 76, 8, 8, shape = "rect", size = 0f, color = RED))
                add(FaceElement("c2", "shape", 114, 104, 8, 8, shape = "rect", size = 0f, color = RED))
                add(FaceElement("minute", "number", 126, 58, 96, bind = Binding.MINUTE.key, font = "space-grotesk", size = 66f, weight = 700, color = WHITE))
                add(FaceElement("rpm", "shape", 18, 156, 204, 14, shape = "segments", count = 15, sweep = 264f, color = RED))
                add(label("spdLabel", "STEPS", 14, 188, 72, align = "center"))
                add(value("steps", Binding.STEPS, 14, 206, 72, size = 22f))
                add(label("hrLabel", "BPM", 86, 188, 68, align = "center"))
                add(value("hr", Binding.HEART_RATE, 86, 206, 68, RED, 22f))
                add(label("fuelLabel", "FUEL", 154, 188, 72, align = "center"))
                add(value("battery", Binding.BATTERY, 154, 206, 72, size = 22f))
            },
        ),
    )

    val f1Speedo = Preset(
        "f1-speedo", "F1 Rev Dial", "A tachometer dial with a red redline, needle hands and steps as speed.",
        FaceSpec(
            name = "F1 Rev Dial",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("track", "shape", 12, CY - 108, 216, 216, shape = "arc", startAngle = 225f, sweep = 270f, stroke = 8f, color = DIM),
                FaceElement("redline", "shape", 12, CY - 108, 216, 216, shape = "arc", startAngle = 90f, sweep = 45f, stroke = 8f, color = RED),
                FaceElement("ticks", "shape", 26, CY - 94, 188, 188, shape = "ticks", count = 27, startAngle = 225f, sweep = 280f, size = 12f, stroke = 3f, color = WHITE),
                label("rpm", "RPM X1000", 0, 92, W, align = "center"),
                label("spd", "STEPS", 0, 196, W, align = "center"),
                value("steps", Binding.STEPS, 0, 214, W, size = 26f),
            ) + hands("needle", hourWidth = 12f, minuteWidth = 10f),
        ),
    )

    val hud = Preset(
        "hud", "HUD", "A head-up display: corner brackets, a target ring, mono time and system readouts.",
        FaceSpec(
            name = "HUD",
            background = Background("#000000"),
            elements = buildList {
                val b = 26; val t = 4; val m = 12
                listOf(m to m, W - m - b to m, m to 280 - m - b, W - m - b to 280 - m - b).forEachIndexed { i, (x, y) ->
                    val right = i % 2 == 1; val bottom = i >= 2
                    add(FaceElement("bh$i", "shape", x, if (bottom) y + b - t else y, b, t, shape = "rect", size = 0f, color = WHITE))
                    add(FaceElement("bv$i", "shape", if (right) x + b - t else x, y, t, b, shape = "rect", size = 0f, color = WHITE))
                }
                add(FaceElement("ring", "shape", 40, 60, 160, 160, shape = "ring", stroke = 3f, color = DIM))
                add(FaceElement("ringTicks", "shape", 40, 60, 160, 160, shape = "ticks", count = 4, size = 12f, stroke = 3f, color = RED))
                add(FaceElement("hour", "number", 0, 108, 116, bind = Binding.HOUR.key, font = "space-mono", size = 44f, weight = 700, align = "right", color = WHITE))
                add(FaceElement("minute", "number", 124, 108, bind = Binding.MINUTE.key, font = "space-mono", size = 44f, weight = 700, color = WHITE))
                add(FaceElement("c1", "shape", CX - 3, 122, 6, 6, shape = "rect", size = 0f, color = RED))
                add(FaceElement("c2", "shape", CX - 3, 138, 6, 6, shape = "rect", size = 0f, color = RED))
                add(label("hrLabel", "HR", 46, 36))
                add(FaceElement("hr", "number", 76, 32, bind = Binding.HEART_RATE.key, glyph = "dot-matrix", size = 21f, color = RED))
                add(label("sysLabel", "SYS", 46, 236))
                add(FaceElement("battery", "number", 84, 232, bind = Binding.BATTERY.key, glyph = "dot-matrix", size = 21f, color = WHITE))
                add(FaceElement("day", "number", 150, 232, 44, bind = Binding.DAY.key, glyph = "dot-matrix", size = 21f, color = GREY, align = "right"))
            },
        ),
    )

    val orbit = Preset(
        "orbit", "Orbit", "Fine orbit rings with a red satellite instead of a seconds hand.",
        FaceSpec(
            name = "Orbit",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("orbit1", "shape", 10, CY - 110, 220, 220, shape = "dot-ring", count = 60, stroke = 2f, color = "#5A5A5A"),
                FaceElement("orbit2", "shape", 50, CY - 70, 140, 140, shape = "ring", stroke = 3f, color = DIM),
                FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key, handStyle = "line", color = WHITE, length = 62, tail = 0, stroke = 9f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("minute", "hand", bind = Binding.MINUTE_HAND.key, handStyle = "line", color = WHITE, length = 96, tail = 0, stroke = 6f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("second", "hand", bind = Binding.SECOND_HAND.key, handStyle = "dot", color = RED, length = 104, stroke = 6f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("cap", "shape", CX - 8, CY - 8, 16, 16, shape = "circle", color = WHITE),
            ),
        ),
    )

    val neonGrid = Preset(
        "neon-grid", "Neon Grid", "Seven-segment time over red ghost segments on a fine grid.",
        FaceSpec(
            name = "Neon Grid",
            background = Background("#000000", pattern = "grid", patternColor = "#1A1A1A", patternSpacing = 20),
            elements = listOf(
                FaceElement("weekday", "sprite", 0, 40, W, bind = Binding.WEEKDAY.key, glyph = "dot-matrix", size = 21f, color = GREY, align = "center"),
                FaceElement("hour", "number", 0, 100, 112, bind = Binding.HOUR.key, glyph = "segment", ghost = "#2A0B0C", size = 64f, color = RED, align = "right"),
                FaceElement("minute", "number", 128, 100, 112, bind = Binding.MINUTE.key, glyph = "segment", ghost = "#2A0B0C", size = 64f, color = RED),
                FaceElement("c1", "shape", CX - 4, 116, 8, 8, shape = "rect", size = 0f, color = RED),
                FaceElement("c2", "shape", CX - 4, 142, 8, 8, shape = "rect", size = 0f, color = RED),
                FaceElement("day", "number", 0, 210, W, bind = Binding.DAY.key, glyph = "segment", size = 28f, color = WHITE, align = "center"),
            ),
        ),
    )

    val oneHand = Preset(
        "one-hand", "One Hand", "A single red hour hand over fine minute marks — read the time to the nearest five minutes.",
        FaceSpec(
            name = "One Hand",
            background = Background("#000000"),
            elements = ticks(color = WHITE, minutes = "#5A5A5A") + listOf(
                FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key, handStyle = "needle", color = RED, length = 100, tail = 18, stroke = 12f, pivotX = CX, pivotY = CY, size = 0f),
                FaceElement("cap", "shape", CX - 8, CY - 8, 16, 16, shape = "circle", color = WHITE),
            ),
        ),
    )

    val dotTime = Preset(
        "dot-time", "Dot Time", "Only the time, in LED dots, centred. Nothing else.",
        FaceSpec(
            name = "Dot Time",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("hour", "number", 0, 108, 112, bind = Binding.HOUR.key, glyph = "dot-matrix", ghost = "#1A1A1A", size = 49f, color = WHITE, align = "right"),
                FaceElement("minute", "number", 128, 108, bind = Binding.MINUTE.key, glyph = "dot-matrix", ghost = "#1A1A1A", size = 49f, color = WHITE),
            ) + colon(120, size = 7, gap = 20),
        ),
    )

    val split = Preset(
        "split", "Split", "Half paper, half black, with red hands crossing both.",
        FaceSpec(
            name = "Split",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("paper", "shape", 0, 0, CX, 280, shape = "rect", size = 0f, color = "#EDEDED"),
                FaceElement("t12", "shape", CX - 3, 18, 6, 22, shape = "rect", size = 0f, color = RED),
                FaceElement("t9", "shape", 14, CY - 3, 22, 6, shape = "rect", size = 0f, color = INK),
                FaceElement("t3", "shape", 204, CY - 3, 22, 6, shape = "rect", size = 0f, color = WHITE),
                FaceElement("t6", "shape", CX - 3, 240, 6, 22, shape = "rect", size = 0f, color = RED),
                FaceElement("day", "number", 150, 196, 60, bind = Binding.DAY.key, font = "space-grotesk", size = 24f, weight = 700, color = WHITE, align = "center"),
                FaceElement("weekday", "sprite", 30, 196, 60, bind = Binding.WEEKDAY.key, font = "space-mono", size = 14f, weight = 700, color = INK, align = "center"),
            ) + hands("bar", color = RED, hourWidth = 12f, minuteWidth = 9f, seconds = false, cap = RED),
        ),
    )

    val dataHero = Preset(
        "data-hero", "Data Hero", "Steps as the hero in Doto; the time stays small at the top.",
        FaceSpec(
            name = "Data Hero",
            background = Background("#000000"),
            elements = listOf(
                FaceElement("hour", "number", 0, 26, 112, bind = Binding.HOUR.key, font = "space-mono", size = 26f, weight = 700, color = WHITE, align = "right"),
                FaceElement("minute", "number", 128, 26, bind = Binding.MINUTE.key, font = "space-mono", size = 26f, weight = 700, color = WHITE),
                FaceElement("c1", "shape", CX - 3, 34, 5, 5, shape = "rect", size = 0f, color = RED),
                FaceElement("c2", "shape", CX - 3, 46, 5, 5, shape = "rect", size = 0f, color = RED),
                label("stepsLabel", "STEPS TODAY", 0, 96, W, align = "center"),
                FaceElement("steps", "number", 0, 114, W, bind = Binding.STEPS.key, font = "doto", size = 56f, weight = 700, color = WHITE, align = "center"),
                FaceElement("bar", "shape", 30, 196, 180, 10, shape = "segments", count = 20, sweep = 360f, color = DIM),
                label("kcalLabel", "KCAL", 30, 222, 80, align = "center"),
                value("kcal", Binding.CALORIES, 30, 240, 80, size = 20f),
                label("bpmLabel", "BPM", 130, 222, 80, align = "center"),
                value("hr", Binding.HEART_RATE, 130, 240, 80, RED, 20f),
            ),
        ),
    )

    val all = listOf(
        dotMatrix, instrument, paper, subdials, glyphRings, minimal, twoHands, indexDots, cloud, braun, railway, radar, paperCloud,
        f1Speedo, orbit, oneHand, split,
        dotMatrixDigital, ledMatrix, segment, stack, glyphData, monoLed, cloudDigital, swissGrid, swissDark, bigDate, terminal,
        f1Telemetry, hud, neonGrid, dotTime, dataHero,
    )
}
