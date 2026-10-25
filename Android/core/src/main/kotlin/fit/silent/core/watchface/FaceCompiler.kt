package fit.silent.core.watchface

/** ARGB_8888 pixels, row-major. */
class Raster(val width: Int, val height: Int, val pixels: IntArray)

/** Vertically stacked equal-height frames (digits, weekdays, …). */
class GlyphStrip(val width: Int, val frameHeight: Int, val frames: Int, val pixels: IntArray) {
    val height get() = frameHeight * frames
}

/** Platform renderer for the parts of a face that need fonts or vector drawing. */
interface FaceRasterizer {
    /** Full-screen background with every static element of [spec] flattened in. */
    fun background(spec: FaceSpec): Raster

    /**
     * Live text frames for [element], each frame the same size so the watch can index them. With [at] (screen
     * top-left of the record) the frames sit on the background found there; otherwise on its sampled colour.
     */
    fun glyphs(spec: FaceSpec, element: FaceElement, frames: List<String>, at: Pair<Int, Int>? = null): GlyphStrip

    /** Full-screen transparent image of the hand pointing at 3 o'clock around its pivot. */
    fun hand(spec: FaceSpec, element: FaceElement): Raster

    /** The whole face with sample values, scaled to [width]×[height] for the watch's face picker. */
    fun preview(spec: FaceSpec, width: Int, height: Int): Raster
}

class CompiledFace(val file: WatchFaceFile, val bytes: ByteArray, val warnings: List<String>)

/** Thrown for designs that would put unverified records on the watch. */
class UnsafeFaceException(message: String) : IllegalArgumentException(message)

/** Records of this element always show one glyph at a fixed spot (sprites, single digits, digit-split time). */
val FaceElement.isFixedGlyph get() = type == "sprite" || binding?.digitIds != null || binding?.maxDigits == 1

/** Screen x of each record of a fixed-glyph element whose frames are [glyphWidth] wide. */
fun FaceElement.glyphSlots(glyphWidth: Int): List<Int> {
    val count = binding?.digitIds?.size ?: 1
    val width = glyphWidth * count
    val start = when {
        align == "right" && w > 0 -> x + w - width
        align == "center" && w > 0 -> x + (w - width) / 2
        else -> x
    }
    return List(count) { start + it * glyphWidth }
}

/** Turns a [FaceSpec] into a watch-installable "WF" file (docs/WATCHFACE_FORMAT.md §4). */
class FaceCompiler(private val rasterizer: FaceRasterizer) {

    fun compile(spec: FaceSpec): CompiledFace {
        val warnings = ArrayList<String>()
        val images = ArrayList<ByteArray>()
        val records = ArrayList<FaceRecord>()

        fun addImage(bytes: ByteArray): Int {
            val existing = images.indexOfFirst { it.contentEquals(bytes) }
            if (existing >= 0) return existing
            images += bytes
            return images.size - 1
        }

        val bg = rasterizer.background(spec)
        records += FaceRecord(FaceRecord.TYPE_IMAGE, WatchFaceFile.ID_STATIC, 1, spec.width / 2, spec.height / 2, addImage(Par.encode(bg.pixels, bg.width, bg.height)), compact = true)

        val pw = PREVIEW_WIDTH * spec.width / 240
        val ph = PREVIEW_HEIGHT * spec.height / 280
        val preview = rasterizer.preview(spec, pw, ph)
        records += FaceRecord(FaceRecord.TYPE_IMAGE, WatchFaceFile.ID_PREVIEW, 1, 0, 0, addImage(Par.encode(preview.pixels, preview.width, preview.height)), compact = true)

        val hands = ArrayList<FaceRecord>()
        for (e in spec.elements) {
            if (!e.isLive) continue
            val binding = e.binding
            if (binding == null || e.recordId != null || e.recordType != null || e.recordName != null) {
                throw UnsafeFaceException("\"${e.id}\" uses a data id that is not proven safe on this watch")
            }
            if (!binding.verified) {
                // Unknown ids crashed the NF 2 firmware into a boot loop (error 00005212); never send them.
                throw UnsafeFaceException("${binding.label} is not supported on this watch yet")
            }
            if (e.type == "hand") {
                val raster = rasterizer.hand(spec, e)
                val image = addImage(Par.encode(raster.pixels, raster.width, raster.height, alpha = true, tile = Par.TILE))
                hands += FaceRecord(
                    type = FaceRecord.TYPE_HAND,
                    dataId = binding.dataId,
                    frames = 1, x = spec.width / 2, y = spec.height / 2, image = image, compact = true,
                    pivotX = e.pivotX, pivotY = e.pivotY, angleTenths = 900,
                )
                continue
            }
            val frames = e.glyphFrames()
            if (e.isFixedGlyph) {
                // One glyph per record, like the official faces: each strip carries the exact background behind it.
                val ids = binding.digitIds ?: listOf(binding.dataId)
                val slots = e.glyphSlots(rasterizer.glyphs(spec, e, frames).width)
                slots.forEachIndexed { i, x ->
                    val strip = rasterizer.glyphs(spec, e, frames, x to e.y)
                    val image = addImage(Par.encode(strip.pixels, strip.width, strip.height))
                    records += FaceRecord(binding.recordType, ids[i], frames.size, x, e.y, image, name = binding.recordName)
                }
                continue
            }
            val strip = rasterizer.glyphs(spec, e, frames)
            val image = addImage(Par.encode(strip.pixels, strip.width, strip.height))
            val anchor = when (e.align) { "right" -> Anchor.RIGHT; "center" -> Anchor.CENTER; else -> Anchor.LEFT }
            val boxWidth = if (e.w > 0) e.w else strip.width * binding.maxDigits
            val wireX = when (anchor) {
                Anchor.RIGHT -> -(spec.width - (e.x + boxWidth))
                // Centred numbers store their centre relative to the screen centre (official sport face: ±62).
                Anchor.CENTER -> e.x + boxWidth / 2 - spec.width / 2
                Anchor.LEFT -> e.x
            }
            val type = binding.recordType
            val name = binding.recordName
            val id = binding.dataId
            binding.placeholderName?.let { emptyName ->
                val dash = rasterizer.glyphs(spec, e, GlyphSet.PLACEHOLDER.frames)
                records += FaceRecord(
                    FaceRecord.TYPE_NUMBER, 0xFF, 1, wireX, e.y, addImage(Par.encode(dash.pixels, dash.width, dash.height)),
                    name = emptyName, anchor = anchor,
                )
            }
            records += FaceRecord(type, id, frames.size, wireX, e.y, image, name = name, anchor = anchor)
        }
        records += hands.sortedBy { it.dataId }

        if (spec.elements.none { it.bind?.startsWith("hand.") == true || it.bind?.startsWith("time.") == true }) {
            warnings += "This face does not show the time"
        }
        val file = WatchFaceFile(spec.width, spec.height, records, images)
        val bytes = file.encode()
        if (bytes.size > MAX_SIZE) warnings += "Face is ${bytes.size / 1024} KB; the watch may reject files over ${MAX_SIZE / 1024} KB"
        return CompiledFace(file, bytes, warnings)
    }

    companion object {
        const val PREVIEW_WIDTH = 164
        const val PREVIEW_HEIGHT = 191
        const val MAX_SIZE = 400 * 1024
    }
}
