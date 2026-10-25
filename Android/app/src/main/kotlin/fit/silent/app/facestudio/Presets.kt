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

    val all = listOf(dotMatrix, instrument, paper, glyphRings)
}
