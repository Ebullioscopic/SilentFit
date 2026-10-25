package fit.silent.core.watchface

import fit.silent.core.protocol.ByteSink
import fit.silent.core.protocol.Bytes

/**
 * Bluetrum "PA" image codec used by Berry/Bluetrum watches for faces and album backgrounds.
 *
 * Layout (docs/WATCHFACE_FORMAT.md §3):
 * ```
 * 0  'P' 'A'  u8 0  u8 alpha  u16 width  u16 height  u8 tile  u8 tableWidth  u16 average  u32 0
 * 16 offset table: (rows|tiles + 1) signed tableWidth-byte deltas; offset[i] = delta[i] + average*i
 *    rows (or 31×31 tiles stepping 30 px) encoded with:
 *    0x00-0x7F  diff  r += ((op>>5)&3)-2, g += ((op>>2)&7)-4, b += (op&3)-2   (RGB565 channels)
 *    0x80|n     run   [alpha u8] rgb565 u16 — repeat n pixels
 *    0xC0|n     literal n × rgb565 u16 (alpha unchanged)
 * ```
 * Pixels are ARGB_8888 ints (`0xAARRGGBB`). Opaque images premultiply RGB by alpha like the vendor tool.
 */
object Par {
    private const val HEADER = 16
    const val TILE = 30

    class Image(val width: Int, val height: Int, val hasAlpha: Boolean, val tile: Int, val pixels: IntArray)

    fun encode(pixels: IntArray, width: Int, height: Int, alpha: Boolean = false, tile: Int = 0): ByteArray {
        require(pixels.size >= width * height) { "pixel buffer too small" }
        val chunks = ArrayList<ByteArray>()
        if (tile > 0) {
            val size = tile + 1
            val buf = IntArray(size * size)
            var y = 0
            while (y < height - 1) {
                var x = 0
                while (x < width - 1) {
                    buf.fill(0)
                    for (yy in 0 until minOf(size, height - y)) for (xx in 0 until minOf(size, width - x)) {
                        buf[yy * size + xx] = pixels[(y + yy) * width + x + xx]
                    }
                    chunks += encodeRun(buf, 0, buf.size, alpha)
                    x += tile
                }
                y += tile
            }
        } else {
            for (y in 0 until height) chunks += encodeRun(pixels, y * width, width, alpha)
        }
        val offsets = LongArray(chunks.size + 1)
        for (i in chunks.indices) offsets[i + 1] = offsets[i] + chunks[i].size
        val count = offsets.size
        val average = minOf(offsets.last() / count, 0xFFFF)
        val deltas = LongArray(count) { offsets[it] - average * it }
        val lo = deltas.min()
        val hi = deltas.max()
        val tableWidth = when {
            lo >= -128 && hi <= 127 -> 1
            lo >= -0x8000 && hi < 0x8000 -> 2
            lo >= -0x800000 && hi < 0x800000 -> 3
            else -> 4
        }
        val out = ByteSink(HEADER + count * tableWidth + offsets.last().toInt())
        out.u8('P'.code).u8('A'.code).u8(0).u8(if (alpha) 1 else 0).u16(width).u16(height).u8(tile).u8(tableWidth).u16(average.toInt()).u32(0)
        for (d in deltas) for (b in 0 until tableWidth) out.u8((d shr (8 * b)).toInt())
        chunks.forEach { out.bytes(it) }
        return out.toByteArray()
    }

    /** Encodes [n] pixels starting at [from]; mirrors the vendor encoder's op choices byte for byte. */
    private fun encodeRun(px: IntArray, from: Int, n: Int, alpha: Boolean): ByteArray {
        val out = ByteSink(n * 2 + 8)
        val threshold = if (alpha) 4 else 3
        val ca = IntArray(n); val cr = IntArray(n); val cg = IntArray(n); val cb = IntArray(n); val c565 = IntArray(n)
        for (i in 0 until n) {
            val p = px[from + i]
            var a = p ushr 24
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            if (!alpha) {
                r = (r * a * 0x8081) ushr 23; g = (g * a * 0x8081) ushr 23; b = (b * a * 0x8081) ushr 23; a = 0
            }
            ca[i] = a; cr[i] = r shr 3; cg[i] = g shr 2; cb[i] = b shr 3
            c565[i] = (cr[i] shl 11) or (cg[i] shl 5) or cb[i]
        }
        var pa = 0; var pr = 0; var pg = 0; var pb = 0; var prgb = 0
        var run = 0; var lastDiff = false; var diffByte = 0; var lit = 0; var litPos = 0

        fun flushRun() {
            if (lastDiff && run < threshold) {
                out.u8(diffByte)
                repeat(run - 1) { out.u8(0x52) }
                diffByte = 0x52
            } else {
                out.u8(0x80 or run)
                if (alpha) out.u8(pa)
                out.u16(prgb)
            }
        }

        fun closeLiteral() { if (lit > 0) { out[litPos] = 0xC0 or lit; lit = 0 } }

        for (i in 0 until n) {
            val a = ca[i]; val r = cr[i]; val g = cg[i]; val b = cb[i]; val c = c565[i]
            if (run > 0) {
                if (run <= 62 && a == pa && c == prgb) { run++; continue }
                flushRun(); run = 0
            }
            if (a == pa) {
                val sameNext = i + 1 < n && c == c565[i + 1]
                val dr = r - pr; val dg = g - pg; val db = b - pb
                if (dr in -2..1 && dg in -4..3 && db in -2..1) {
                    diffByte = (((dr + 2) shl 5) and 0x60) or (((dg + 4) shl 2) and 0x1C) or (db + 2)
                    if (sameNext) run = 1 else out.u8(diffByte)
                    lastDiff = true
                    closeLiteral()
                    pa = a; pr = r; pg = g; pb = b; prgb = c
                    continue
                }
                if (!sameNext) {
                    if (lit == 0) { litPos = out.size; out.u8(0) }
                    out.u16(c)
                    lit++; lastDiff = false
                    if (lit >= 63) closeLiteral()
                    pa = a; pr = r; pg = g; pb = b; prgb = c
                    continue
                }
            }
            lastDiff = false; run = 1
            closeLiteral()
            pa = a; pr = r; pg = g; pb = b; prgb = c
        }
        if (run > 0) flushRun()
        closeLiteral()
        return out.toByteArray()
    }

    /** Header-only inspection; returns null when [data] at [offset] is not a PAR image. */
    fun header(data: ByteArray, offset: Int = 0): IntArray? {
        if (offset + HEADER > data.size || data[offset] != 'P'.code.toByte() || data[offset + 1] != 'A'.code.toByte()) return null
        return intArrayOf(Bytes.u16le(data, offset + 4), Bytes.u16le(data, offset + 6), Bytes.u8(data, offset + 3), Bytes.u8(data, offset + 8))
    }

    /** Decodes an image and returns it with the number of bytes it occupies. */
    fun decode(data: ByteArray, offset: Int = 0): Pair<Image, Int> {
        val h = header(data, offset) ?: throw IllegalArgumentException("Not a PAR image")
        val (width, height, alphaFlag, tile) = h.toList()
        val alpha = alphaFlag == 1
        val tableWidth = Bytes.u8(data, offset + 9)
        val average = Bytes.u16le(data, offset + 10).toLong()
        val count = if (tile == 0) height + 1 else ((width + tile - 2) / tile) * ((height + tile - 2) / tile) + 1
        val table = LongArray(count) { i ->
            var v = 0L
            val p = offset + HEADER + i * tableWidth
            for (b in 0 until tableWidth) v = v or ((data[p + b].toLong() and 0xFF) shl (8 * b))
            val shift = 64 - 8 * tableWidth
            ((v shl shift) shr shift) + average * i
        }
        val dataStart = offset + HEADER + count * tableWidth
        val pixels = IntArray(width * height)
        if (tile == 0) {
            for (y in 0 until height) decodeRun(data, dataStart + table[y].toInt(), width, alpha, pixels, y * width)
        } else {
            val size = tile + 1
            val tx = (width + tile - 2) / tile
            val ty = (height + tile - 2) / tile
            val tmp = IntArray(size * size)
            for (j in 0 until ty) for (i in 0 until tx) {
                decodeRun(data, dataStart + table[j * tx + i].toInt(), size * size, alpha, tmp, 0)
                for (yy in 0 until size) for (xx in 0 until size) {
                    val x = i * tile + xx; val y = j * tile + yy
                    if (x < width && y < height) pixels[y * width + x] = tmp[yy * size + xx]
                }
            }
        }
        return Image(width, height, alpha, tile, pixels) to (dataStart + table.last().toInt() - offset)
    }

    private fun decodeRun(d: ByteArray, start: Int, n: Int, alpha: Boolean, out: IntArray, outFrom: Int) {
        var pos = start
        var a = if (alpha) 0 else 255
        var r = 0; var g = 0; var b = 0
        var i = 0
        fun put() { if (i < n) out[outFrom + i] = (a shl 24) or (expand5(r) shl 16) or (expand6(g) shl 8) or expand5(b); i++ }
        while (i < n && pos < d.size) {
            val op = d[pos++].toInt() and 0xFF
            when {
                op < 0x80 -> { r += ((op shr 5) and 3) - 2; g += ((op shr 2) and 7) - 4; b += (op and 3) - 2; put() }
                op < 0xC0 -> {
                    if (alpha) a = d[pos++].toInt() and 0xFF
                    val c = Bytes.u16le(d, pos); pos += 2
                    r = c shr 11; g = (c shr 5) and 0x3F; b = c and 0x1F
                    repeat(op and 0x3F) { put() }
                }
                else -> repeat(op and 0x3F) {
                    val c = Bytes.u16le(d, pos); pos += 2
                    r = c shr 11; g = (c shr 5) and 0x3F; b = c and 0x1F
                    put()
                }
            }
        }
    }

    private fun expand5(v: Int) = ((v and 0x1F) shl 3) or ((v and 0x1F) shr 2)
    private fun expand6(v: Int) = ((v and 0x3F) shl 2) or ((v and 0x3F) shr 4)
}
