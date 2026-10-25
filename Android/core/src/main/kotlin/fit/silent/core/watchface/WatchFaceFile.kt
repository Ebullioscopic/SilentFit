package fit.silent.core.watchface

import fit.silent.core.protocol.ByteSink
import fit.silent.core.protocol.Bytes

/** Horizontal anchor of a long record; negative x values measure from the right edge (centre: right half). */
enum class Anchor(val wire: Int) { LEFT(1), CENTER(2), RIGHT(3);
    companion object { fun of(v: Int) = entries.firstOrNull { it.wire == v } ?: LEFT }
}

/**
 * One layout record of a Bluetrum "WF" face (docs/WATCHFACE_FORMAT.md §2).
 *
 * @property type 1 = rotating hand, 2 = image / image sprite, 4 = number or indexed sprite
 * @property dataId binding id; 0x10 static, 0x11 preview, 0xFF = bound by [name]
 * @property frames number of vertically stacked frames in the image (digits = 10)
 */
data class FaceRecord(
    val type: Int,
    val dataId: Int,
    val frames: Int,
    val x: Int,
    val y: Int,
    val image: Int,
    val compact: Boolean = false,
    val name: String = "",
    val numberFlag: Int = if (type == TYPE_NUMBER) 1 else 0,
    val pivotX: Int = 0,
    val pivotY: Int = 0,
    val angleTenths: Int = 900,
    val anchor: Anchor = Anchor.LEFT,
) {
    companion object {
        const val TYPE_HAND = 1
        const val TYPE_IMAGE = 2
        const val TYPE_NUMBER = 4
        const val SHORT_BODY = 16
        const val LONG_BODY = 39
        private val LONG_TAIL = Bytes.of(0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
        private val NAME_LEFT = "extern!I".toByteArray()
        private val NAME_RIGHT = "externci".toByteArray()
        private val NAME_CENTER = "externBy".toByteArray()

        internal fun write(out: ByteSink, r: FaceRecord) {
            out.u8(r.type).u8(r.dataId).u16(r.frames).u16(r.x).u16(r.y)
            when (r.type) {
                TYPE_HAND -> out.u16(r.pivotX).u16(r.pivotY).u32(r.angleTenths)
                TYPE_NUMBER -> {
                    out.u8(r.numberFlag)
                    val n = r.name.toByteArray(Charsets.US_ASCII).copyOf(7)
                    out.bytes(n)
                }
                else -> out.zeros(8)
            }
            if (!r.compact) {
                out.bytes(when (r.anchor) { Anchor.RIGHT -> NAME_RIGHT; Anchor.CENTER -> NAME_CENTER; else -> NAME_LEFT }).bytes(LONG_TAIL).u8(r.anchor.wire)
            }
        }

        internal fun read(d: ByteArray, at: Int, image: Int, compact: Boolean): FaceRecord {
            val type = Bytes.u8(d, at)
            val name = if (type == TYPE_NUMBER) String(d, at + 9, 7, Charsets.US_ASCII).trimEnd('\u0000') else ""
            return FaceRecord(
                type = type,
                dataId = Bytes.u8(d, at + 1),
                frames = Bytes.u16le(d, at + 2),
                x = Bytes.s16le(d, at + 4),
                y = Bytes.s16le(d, at + 6),
                image = image,
                compact = compact,
                name = name,
                numberFlag = if (type == TYPE_NUMBER) Bytes.u8(d, at + 8) else 0,
                pivotX = if (type == TYPE_HAND) Bytes.u16le(d, at + 8) else 0,
                pivotY = if (type == TYPE_HAND) Bytes.u16le(d, at + 10) else 0,
                angleTenths = if (type == TYPE_HAND) Bytes.u32le(d, at + 12).toInt() else 900,
                anchor = if (compact) Anchor.CENTER else Anchor.of(Bytes.u8(d, at + LONG_BODY - 1)),
            )
        }
    }
}

/**
 * A complete "WF" watch face: header, layout records and the PAR images they reference.
 * Records point at images by index; identical images are stored once.
 */
class WatchFaceFile(
    val width: Int,
    val height: Int,
    val records: List<FaceRecord>,
    val images: List<ByteArray>,
    /** Header words at 0x08/0x0A; official faces use either 1/1 or 0/0. */
    val headerCounts: Pair<Int, Int> = 1 to 1,
) {

    fun encode(): ByteArray {
        val tableSize = records.sumOf { 4 + if (it.compact) FaceRecord.SHORT_BODY else FaceRecord.LONG_BODY }
        val dataStart = HEADER + tableSize
        val unique = LinkedHashMap<Int, Int>()
        val blobOffsets = ArrayList<Int>()
        val blobs = ArrayList<ByteArray>()
        var cursor = dataStart
        val recordOffsets = records.map { r ->
            val existing = unique[r.image]
            if (existing != null) existing else {
                val bytes = images[r.image]
                val dup = blobs.indexOfFirst { it.contentEquals(bytes) }
                val off = if (dup >= 0) blobOffsets[dup] else cursor.also { blobs += bytes; blobOffsets += it; cursor += bytes.size }
                unique[r.image] = off
                off
            }
        }
        val out = ByteSink(cursor)
        out.u8('W'.code).u8('F'.code).u16(VERSION).u32(0)
        out.u16(headerCounts.first)
        out.u16(headerCounts.second)
        out.zeros(0x2A - out.size).u16(width).u16(height).u16(records.size).u32(cursor)
        records.forEachIndexed { i, r ->
            out.u32(recordOffsets[i])
            FaceRecord.write(out, r)
        }
        check(out.size == dataStart)
        blobs.forEach { out.bytes(it) }
        return out.toByteArray()
    }

    companion object {
        const val HEADER = 0x34
        const val VERSION = 0x0414
        const val ID_STATIC = 0x10
        const val ID_PREVIEW = 0x11

        fun isFace(d: ByteArray) = d.size > HEADER && d[0] == 'W'.code.toByte() && d[1] == 'F'.code.toByte()

        /** Long records carry an "extern" tag after the 16-byte body; image offsets can never spell it. */
        fun isLongRecord(d: ByteArray, body: Int) =
            body + FaceRecord.LONG_BODY <= d.size && String(d, body + FaceRecord.SHORT_BODY, 6, Charsets.US_ASCII) == "extern"

        fun decode(d: ByteArray): WatchFaceFile {
            require(isFace(d)) { "Not a WF watch face" }
            val width = Bytes.u16le(d, 0x2A)
            val height = Bytes.u16le(d, 0x2C)
            val count = Bytes.u16le(d, 0x2E)
            val imageIndex = LinkedHashMap<Int, Int>()
            val images = ArrayList<ByteArray>()
            val records = ArrayList<FaceRecord>(count)
            var p = HEADER
            for (i in 0 until count) {
                val offset = Bytes.u32le(d, p).toInt()
                val compact = !isLongRecord(d, p + 4)
                val idx = imageIndex.getOrPut(offset) {
                    val (_, size) = Par.decode(d, offset)
                    images += d.copyOfRange(offset, offset + size)
                    images.size - 1
                }
                records += FaceRecord.read(d, p + 4, idx, compact)
                p += 4 + if (compact) FaceRecord.SHORT_BODY else FaceRecord.LONG_BODY
            }
            return WatchFaceFile(width, height, records, images, Bytes.u16le(d, 0x08) to Bytes.u16le(d, 0x0A))
        }
    }
}
