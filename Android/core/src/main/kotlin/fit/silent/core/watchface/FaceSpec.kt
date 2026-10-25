package fit.silent.core.watchface

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A watch face design ("SilentFit face file", `*.sfface.json` or `*.sfface` zip).
 *
 * Static elements (text, shapes, images, background) are flattened into one background image when compiled;
 * live elements (numbers, sprites, hands) become watch records. See docs/WATCHFACE_DESIGN.md.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FaceSpec(
    @EncodeDefault val format: String = FORMAT,
    @EncodeDefault val version: Int = 1,
    val name: String = "Untitled",
    val author: String = "",
    val width: Int = 240,
    val height: Int = 280,
    val background: Background = Background(),
    val elements: List<FaceElement> = emptyList(),
    /** Inline assets referenced by `image` elements and backgrounds: name → base64 PNG/JPEG. */
    val assets: Map<String, String> = emptyMap(),
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    fun withElement(id: String, transform: (FaceElement) -> FaceElement) =
        copy(elements = elements.map { if (it.id == id) transform(it) else it })

    companion object {
        const val FORMAT = "silentfit.face"
        val JSON = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

        fun fromJson(text: String): FaceSpec {
            val spec = JSON.decodeFromString(serializer(), text)
            require(spec.format == FORMAT) { "Not a SilentFit face (format \"${spec.format}\")" }
            require(spec.width in 64..1024 && spec.height in 64..1024) { "Unsupported screen size" }
            return spec
        }
    }
}

@Serializable
data class Background(
    val color: String = "#000000",
    /** Asset name of a full-screen image drawn over [color]. */
    val image: String? = null,
    /** "none", "dots", "grid" or "lines". */
    val pattern: String = "none",
    val patternColor: String = "#222222",
    val patternSpacing: Int = 12,
)

/**
 * A single layer. [type] is one of:
 * - `number`  live value from [bind] drawn with [font]
 * - `sprite`  live indexed text (weekday, AM/PM) drawn with [font]
 * - `hand`    rotating hand ([bind] = hand.hour / hand.minute / hand.second)
 * - `text`    static text
 * - `shape`   static `rect`, `circle`, `ring`, `arc`, `ticks`, `dot-ring`, `line`, `segments`
 * - `image`   static asset
 */
@Serializable
data class FaceElement(
    val id: String,
    val type: String,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
    val bind: String? = null,
    val text: String? = null,
    val font: String = "space-mono",
    val size: Float = 16f,
    val weight: Int = 400,
    val color: String = "#FFFFFF",
    /** "left" or "right" — numbers grow away from this edge. Static text also accepts "center". */
    val align: String = "left",
    val tracking: Float = 0f,
    val uppercase: Boolean = false,
    val shape: String? = null,
    val stroke: Float = 2f,
    val fill: Boolean = true,
    val startAngle: Float = 0f,
    val sweep: Float = 360f,
    val count: Int = 12,
    val src: String? = null,
    /** Hand options. */
    val length: Int = 80,
    val tail: Int = 12,
    val handStyle: String = "bar",
    val pivotX: Int = 120,
    val pivotY: Int = 140,
    /** Advanced: override the watch record (see docs §5). */
    val recordType: Int? = null,
    val recordId: Int? = null,
    val recordName: String? = null,
    val frames: List<String>? = null,
    val opacity: Float = 1f,
    val locked: Boolean = false,
) {
    val isLive get() = type == "number" || type == "sprite" || type == "hand"
    val binding get() = bind?.let { Binding.of(it) }

    /** Frames drawn for live text, honouring overrides. */
    fun glyphFrames(): List<String> = frames ?: (binding?.glyphs ?: GlyphSet.DIGITS).frames.map { if (uppercase) it.uppercase() else it }
}

/** Parses `#RGB`, `#RRGGBB` or `#AARRGGBB` into ARGB. */
fun parseColor(s: String, fallback: Int = 0xFFFFFFFF.toInt()): Int {
    val h = s.removePrefix("#")
    return try {
        when (h.length) {
            3 -> (0xFF shl 24) or h.map { "$it$it".toInt(16) }.let { (r, g, b) -> (r shl 16) or (g shl 8) or b }
            6 -> (0xFF000000.toInt()) or h.toInt(16)
            8 -> h.toLong(16).toInt()
            else -> fallback
        }
    } catch (_: NumberFormatException) {
        fallback
    }
}

fun colorHex(argb: Int): String =
    if (argb ushr 24 == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
