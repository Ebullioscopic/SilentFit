package fit.silent.core.protocol

/** Minimal protobuf (proto2) writer covering the Berry command set. */
class ProtoWriter {
    private val sink = ByteSink(64)

    val bytes: ByteArray get() = sink.toByteArray()

    private fun varint(value: Long) {
        var v = value
        repeat(10) {
            val low = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) { sink.u8(low); return }
            sink.u8(low or 0x80)
        }
    }

    private fun key(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    fun uint(field: Int, value: Int): ProtoWriter { key(field, 0); varint(value.toLong()); return this }

    fun uint64(field: Int, value: Long): ProtoWriter { key(field, 0); varint(value); return this }

    fun sint(field: Int, value: Int): ProtoWriter {
        val v = value.toLong()
        key(field, 0); varint((v shl 1) xor (v shr 63)); return this
    }

    fun bool(field: Int, value: Boolean): ProtoWriter = uint(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        key(field, 2); varint(value.size.toLong()); sink.bytes(value); return this
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, value: ProtoWriter): ProtoWriter = bytes(field, value.bytes)

    fun float(field: Int, value: Float): ProtoWriter {
        key(field, 5); sink.u32(java.lang.Float.floatToIntBits(value).toLong() and 0xFFFFFFFFL); return this
    }

    /** Writes each value as its own field (unpacked repeated varint). */
    fun uints(field: Int, values: List<Int>): ProtoWriter { values.forEach { uint(field, it) }; return this }
}

class ProtoField(val number: Int, val wireType: Int, val varint: Long, val data: ByteArray)

/** A decoded protobuf message as an ordered field list with typed accessors. */
class ProtoMessage(val fields: List<ProtoField>) {
    fun has(n: Int) = fields.any { it.number == n }

    fun long(n: Int): Long? = fields.lastOrNull { it.number == n && it.wireType != 2 }?.varint

    fun int(n: Int): Int? = long(n)?.toInt()

    fun sint(n: Int): Int? = long(n)?.let { ((it ushr 1) xor -(it and 1)).toInt() }

    fun bool(n: Int): Boolean? = long(n)?.let { it != 0L }

    fun float(n: Int): Float? = long(n)?.let { java.lang.Float.intBitsToFloat(it.toInt()) }

    fun bytes(n: Int): ByteArray? = fields.lastOrNull { it.number == n && it.wireType == 2 }?.data

    fun string(n: Int): String? = bytes(n)?.toString(Charsets.UTF_8)

    fun message(n: Int): ProtoMessage? = bytes(n)?.let { parse(it) }

    fun messages(n: Int): List<ProtoMessage> = fields.filter { it.number == n && it.wireType == 2 }.mapNotNull { parse(it.data) }

    fun strings(n: Int): List<String> = fields.filter { it.number == n && it.wireType == 2 }.map { it.data.toString(Charsets.UTF_8) }

    fun rawRepeated(n: Int): List<ByteArray> = fields.filter { it.number == n && it.wireType == 2 }.map { it.data }

    /** Repeated varint field, accepting packed and unpacked encodings. */
    fun ints(n: Int): List<Int> {
        val out = ArrayList<Int>()
        for (f in fields) {
            if (f.number != n) continue
            if (f.wireType == 0) out.add(f.varint.toInt())
            else if (f.wireType == 2) {
                var i = 0
                while (i < f.data.size) {
                    val v = readVarint(f.data, i) ?: break
                    out.add(v.first.toInt()); i = v.second
                }
            }
        }
        return out
    }

    companion object {
        fun parse(b: ByteArray): ProtoMessage? {
            val fields = ArrayList<ProtoField>()
            var i = 0
            while (i < b.size) {
                val (keyValue, next) = readVarint(b, i) ?: return null
                i = next
                val number = (keyValue ushr 3).toInt()
                val wire = (keyValue and 7).toInt()
                if (number <= 0) return null
                when (wire) {
                    0 -> { val (v, n) = readVarint(b, i) ?: return null; fields.add(ProtoField(number, 0, v, EMPTY)); i = n }
                    2 -> {
                        val (len, n) = readVarint(b, i) ?: return null
                        val end = n + len.toInt()
                        if (len < 0 || end > b.size) return null
                        fields.add(ProtoField(number, 2, 0, b.copyOfRange(n, end))); i = end
                    }
                    5 -> { if (i + 4 > b.size) return null; fields.add(ProtoField(number, 5, Bytes.u32le(b, i), EMPTY)); i += 4 }
                    1 -> {
                        if (i + 8 > b.size) return null
                        fields.add(ProtoField(number, 1, Bytes.u32le(b, i) or (Bytes.u32le(b, i + 4) shl 32), EMPTY)); i += 8
                    }
                    else -> return null
                }
            }
            return ProtoMessage(fields)
        }

        fun readVarint(b: ByteArray, start: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var i = start
            while (i < b.size && shift < 64) {
                val v = b[i].toInt() and 0xFF
                result = result or ((v and 0x7F).toLong() shl shift)
                i++
                if (v and 0x80 == 0) return result to i
                shift += 7
            }
            return null
        }

        private val EMPTY = ByteArray(0)
    }
}
