package fit.silent.core.protocol

/** Little-endian byte helpers shared by the Berry protocol and file formats. */
object Bytes {
    fun u8(b: ByteArray, at: Int): Int = if (at in b.indices) b[at].toInt() and 0xFF else 0

    fun u16le(b: ByteArray, at: Int): Int = u8(b, at) or (u8(b, at + 1) shl 8)

    fun s16le(b: ByteArray, at: Int): Int = u16le(b, at).toShort().toInt()

    fun u32le(b: ByteArray, at: Int): Long =
        (u8(b, at).toLong()) or (u8(b, at + 1).toLong() shl 8) or (u8(b, at + 2).toLong() shl 16) or (u8(b, at + 3).toLong() shl 24)

    fun le16(v: Int): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte())

    fun le32(v: Long): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    fun le32(v: Int): ByteArray = le32(v.toLong())

    fun of(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    fun hex(b: ByteArray, separator: String = " "): String = b.joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

    fun fromHex(s: String): ByteArray {
        val clean = s.filter { it.isLetterOrDigit() }
        require(clean.length % 2 == 0) { "Odd hex length" }
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun indexOf(needle: ByteArray, haystack: ByteArray, from: Int = 0, until: Int = haystack.size): Int {
        if (needle.isEmpty()) return from
        var i = from
        while (i + needle.size <= until) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { match = false; break }
            }
            if (match) return i
            i++
        }
        return -1
    }

    fun startsWith(b: ByteArray, prefix: ByteArray, at: Int = 0, until: Int = b.size): Boolean {
        if (until - at < prefix.size) return false
        for (i in prefix.indices) if (b[at + i] != prefix[i]) return false
        return true
    }
}

/** Growable byte buffer with little-endian writers. */
class ByteSink(initial: Int = 256) {
    private var buf = ByteArray(initial)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
    }

    fun u8(v: Int): ByteSink { ensure(1); buf[size++] = v.toByte(); return this }

    fun u16(v: Int): ByteSink { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte(); return this }

    fun u32(v: Long): ByteSink {
        ensure(4)
        for (i in 0 until 4) buf[size++] = (v shr (8 * i)).toByte()
        return this
    }

    fun u32(v: Int): ByteSink = u32(v.toLong())

    fun bytes(b: ByteArray, from: Int = 0, to: Int = b.size): ByteSink {
        val n = to - from
        ensure(n)
        System.arraycopy(b, from, buf, size, n)
        size += n
        return this
    }

    fun zeros(n: Int): ByteSink { ensure(n); size += n; return this }

    operator fun set(index: Int, value: Int) { buf[index] = value.toByte() }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}
