package fit.silent.core.watchface

import fit.silent.core.protocol.Bytes

/**
 * Structural gate run on every "WF" file before it may be sent to the watch, whatever its source
 * (studio, `.sfface` import or a raw WF file).
 *
 * The NF 2 firmware does not validate faces: an unknown record id put it into a boot loop (error 00005212)
 * that only a factory reset cleared. So this check is an allowlist, not a sanity check — every record must
 * match a pattern found in the official face for this watch (docs/WATCHFACE_FORMAT.md §6), byte for byte
 * where the vendor bytes are fixed. Anything else is rejected.
 */
object FaceSafety {
    const val WIDTH = 240
    const val HEIGHT = 280
    const val MAX_BYTES = 400 * 1024
    const val MAX_RECORDS = 64
    /** Conservative size limits for live glyph frames (the official face uses ≤ 36×13 px). */
    const val MAX_GLYPH = 120
    const val MAX_STRIP = 1200

    /** A record shape taken from the official face: (type, data id, frames, name). */
    private data class Pattern(val type: Int, val id: Int, val frames: Int, val name: String = "", val unique: Boolean = true)

    private val LONG_PATTERNS = listOf(
        Pattern(FaceRecord.TYPE_IMAGE, WatchFaceFile.ID_STATIC, 1, unique = false),
        Pattern(FaceRecord.TYPE_IMAGE, 0x1F, 2),
        Pattern(FaceRecord.TYPE_NUMBER, 0x02, 10),
        Pattern(FaceRecord.TYPE_NUMBER, 0x03, 10),
        Pattern(FaceRecord.TYPE_NUMBER, 0x40, 10),
        Pattern(FaceRecord.TYPE_NUMBER, 0x41, 10),
        Pattern(FaceRecord.TYPE_NUMBER, 0xFF, 7, "PICBOX1"),
        Pattern(FaceRecord.TYPE_NUMBER, 0xFF, 10, "NUMBER4"),
        Pattern(FaceRecord.TYPE_NUMBER, 0xFF, 1, "N_DATA4"),
        Pattern(FaceRecord.TYPE_NUMBER, 0xFF, 10, "NUMBER2"),
    )
    private val HAND_IDS = listOf(0x05, 0x06)
    private val LONG_TAIL = Bytes.of(0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)

    /** Returns the problems found; an empty list means the file only uses proven structures. */
    fun check(d: ByteArray): List<String> {
        val problems = ArrayList<String>()
        try {
            inspect(d, problems)
        } catch (e: Exception) {
            problems += "File is damaged (${e.message ?: e.javaClass.simpleName})"
        }
        return problems
    }

    fun isSafe(d: ByteArray) = check(d).isEmpty()

    private fun inspect(d: ByteArray, problems: MutableList<String>) {
        if (!WatchFaceFile.isFace(d)) { problems += "Not a WF watch face"; return }
        if (d.size > MAX_BYTES) problems += "File is larger than ${MAX_BYTES / 1024} KB"
        if (Bytes.u16le(d, 2) != WatchFaceFile.VERSION) problems += "Unknown WF version"
        if (Bytes.u32le(d, 4) != 0L) problems += "Unexpected header flags"
        if (Bytes.u16le(d, 0x08) != 1 || Bytes.u16le(d, 0x0A) != 1) problems += "Face must start with one background and one preview"
        if ((0x0C until 0x2A).any { d[it].toInt() != 0 }) problems += "Unexpected header data"
        val width = Bytes.u16le(d, 0x2A)
        val height = Bytes.u16le(d, 0x2C)
        val count = Bytes.u16le(d, 0x2E)
        if (width != WIDTH || height != HEIGHT) problems += "Face is ${width}×$height; this watch needs ${WIDTH}×$HEIGHT"
        if (Bytes.u32le(d, 0x30) != d.size.toLong()) problems += "Size field does not match the file"
        if (count < 2 || count > MAX_RECORDS) { problems += "Unsupported number of elements ($count)"; return }
        if (problems.isNotEmpty()) return

        val seen = HashSet<Pattern>()
        val hands = ArrayList<Int>()
        var tableEnd = WatchFaceFile.HEADER
        var p = WatchFaceFile.HEADER
        var previousName = ""
        val imageEnds = ArrayList<Pair<Int, Int>>()
        for (i in 0 until count) {
            if (p + 4 + FaceRecord.SHORT_BODY > d.size) { problems += "Record table is truncated"; return }
            val offset = Bytes.u32le(d, p).toInt()
            val b = p + 4
            val type = Bytes.u8(d, b)
            val id = Bytes.u8(d, b + 1)
            val frames = Bytes.u16le(d, b + 2)
            val x = Bytes.s16le(d, b + 4)
            val y = Bytes.s16le(d, b + 6)
            val compact = i < 2 || type == FaceRecord.TYPE_HAND
            val bodySize = if (compact) FaceRecord.SHORT_BODY else FaceRecord.LONG_BODY
            if (b + bodySize > d.size) { problems += "Record table is truncated"; return }
            val label = "Element ${i + 1}"
            val image = Par.header(d, offset)
            if (image == null) { problems += "$label: image is missing"; return }
            val (iw, ih, alpha, tile) = image.toList()

            when {
                i == 0 -> {
                    if (type != FaceRecord.TYPE_IMAGE || id != WatchFaceFile.ID_STATIC || frames != 1 || x != WIDTH / 2 || y != HEIGHT / 2) problems += "First element must be the background"
                    if (iw != WIDTH || ih != HEIGHT || alpha != 0 || tile != 0) problems += "Background must be an opaque ${WIDTH}×$HEIGHT image"
                    if (!zeros(d, b + 8, 8)) problems += "$label: unexpected data"
                }
                i == 1 -> {
                    if (type != FaceRecord.TYPE_IMAGE || id != WatchFaceFile.ID_PREVIEW || frames != 1 || x != 0 || y != 0) problems += "Second element must be the preview"
                    if (iw != FaceCompiler.PREVIEW_WIDTH || ih != FaceCompiler.PREVIEW_HEIGHT || alpha != 0 || tile != 0) problems += "Preview must be an opaque ${FaceCompiler.PREVIEW_WIDTH}×${FaceCompiler.PREVIEW_HEIGHT} image"
                    if (!zeros(d, b + 8, 8)) problems += "$label: unexpected data"
                }
                type == FaceRecord.TYPE_HAND -> {
                    if (id !in HAND_IDS) problems += "$label: hand type is not supported on this watch"
                    else if (hands.isNotEmpty() && id <= hands.last()) problems += "$label: hands are out of order or repeated"
                    hands += id
                    if (frames != 1 || x != WIDTH / 2 || y != HEIGHT / 2) problems += "$label: hand must be centred"
                    if (iw != WIDTH || ih != HEIGHT || alpha != 1 || tile != Par.TILE) problems += "$label: hand must be a full-screen transparent image"
                    val px = Bytes.u16le(d, b + 8); val py = Bytes.u16le(d, b + 10)
                    if (px != WIDTH / 2 || py != HEIGHT / 2) problems += "$label: hands must turn around the screen centre"
                    if (Bytes.u32le(d, b + 12) != 900L) problems += "$label: unexpected hand angle"
                }
                else -> {
                    if (hands.isNotEmpty()) problems += "$label: hands must be the last elements"
                    val name = if (type == FaceRecord.TYPE_NUMBER) String(d, b + 9, 7, Charsets.US_ASCII).trimEnd('\u0000') else ""
                    val pattern = LONG_PATTERNS.firstOrNull { it.type == type && it.id == id && it.frames == frames && it.name == name }
                    if (pattern == null) {
                        problems += "$label: data type ${"%02X".format(id)}${if (name.isNotEmpty()) " $name" else ""} is not proven safe on this watch"
                    } else {
                        if (pattern.unique && !seen.add(pattern)) problems += "$label: ${pattern.name.ifEmpty { "%02X".format(id) }} appears twice"
                        if (name == "NUMBER4" && previousName != "N_DATA4") problems += "$label: heart rate needs its no-data placeholder first"
                    }
                    if (previousName == "N_DATA4" && name != "NUMBER4") problems += "$label: placeholder must be followed by heart rate"
                    previousName = name
                    val extra = if (type == FaceRecord.TYPE_NUMBER) Bytes.u8(d, b + 8) == 1 else zeros(d, b + 8, 8)
                    if (!extra) problems += "$label: unexpected data"
                    val anchor = Bytes.u8(d, b + FaceRecord.LONG_BODY - 1)
                    val tag = String(d, b + 16, 8, Charsets.US_ASCII)
                    val tailOk = (0 until LONG_TAIL.size).all { d[b + 24 + it] == LONG_TAIL[it] }
                    if (!tailOk || !((tag == "extern!I" && anchor == 1) || (tag == "externci" && anchor == 3))) problems += "$label: unexpected layout data"
                    if (alpha != 0 || tile != 0) problems += "$label: image must be opaque"
                    if (frames == 0 || ih % frames != 0) problems += "$label: image does not split into $frames frames"
                    val frameHeight = if (frames == 0) ih else ih / frames
                    if (iw !in 1..MAX_GLYPH || frameHeight !in 1..MAX_GLYPH || ih > MAX_STRIP) problems += "$label: text is too large (max ${MAX_GLYPH} px per character)"
                    if (x !in -WIDTH..WIDTH || y !in 0..(HEIGHT - frameHeight)) problems += "$label: element is off screen"
                }
            }
            imageEnds += offset to imageEnd(d, offset)
            p += 4 + bodySize
            tableEnd = p
        }
        if (previousName == "N_DATA4") problems += "Heart-rate placeholder is missing its number"
        for ((offset, end) in imageEnds) {
            if (offset < tableEnd || end > d.size) { problems += "Image data points outside the file"; break }
        }
    }

    private fun zeros(d: ByteArray, from: Int, n: Int) = (from until from + n).all { d[it].toInt() == 0 }

    /** End of the PAR image at [offset]; also verifies every row/tile offset stays inside the image data. */
    private fun imageEnd(d: ByteArray, offset: Int): Int {
        val h = Par.header(d, offset) ?: return Int.MAX_VALUE
        val (width, height, _, tile) = h.toList()
        val tableWidth = Bytes.u8(d, offset + 9)
        if (tableWidth !in 1..4 || (tile != 0 && tile != Par.TILE) || width == 0 || height == 0) return Int.MAX_VALUE
        if (Bytes.u8(d, offset + 2) != 0 || Bytes.u32le(d, offset + 12) != 0L) return Int.MAX_VALUE
        val (_, size) = Par.decode(d, offset)
        return offset + size
    }
}
