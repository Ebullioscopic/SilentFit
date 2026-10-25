package fit.silent.core.protocol

/**
 * Berry link-layer framing (docs/PROTOCOL.md §2).
 *
 * ```
 * 66 66 48 5A | serial | event | type | len u16 | payload   app request / watch response
 * 88 88 48 5A | serial | event | type | len u16 | payload   watch request
 * 99 99 48 5A | serial | error | 00 | 00                     acknowledgement
 * 11 11 48 5A | serial | event | type | len u16 | payload   watch → app large-file header
 * 22|33|44 48 5A | len u16 | index | payload                watch → app large-file chunk
 * ```
 */
object BerryFrame {
    val HEAD_APP = Bytes.of(0x66, 0x66, 0x48, 0x5A)
    val HEAD_DEVICE = Bytes.of(0x88, 0x88, 0x48, 0x5A)
    val HEAD_ACK = Bytes.of(0x99, 0x99, 0x48, 0x5A)
    val HEAD_FILE = Bytes.of(0x11, 0x11, 0x48, 0x5A)
    private val CHUNK_MARKERS = setOf(0x22, 0x33, 0x44, 0x55, 0x66, 0x77)
    private val FILE_CHUNK_MARKERS = setOf(0x22, 0x33, 0x44)

    const val EVENT_PROTOBUF = 1
    const val EVENT_LARGE_FILE = 2
    const val EVENT_PRIVATE = 3
    const val TYPE_PROTOBUF = 3
    const val TYPE_PRIVATE = 2
    const val HEADER_SIZE = 9

    fun encode(serial: Int, event: Int, type: Int, payload: ByteArray): ByteArray =
        ByteSink(payload.size + HEADER_SIZE).bytes(HEAD_APP).u8(serial).u8(event).u8(type).u16(payload.size).bytes(payload).toByteArray()

    fun ack(serial: Int): ByteArray = ByteSink(8).bytes(HEAD_ACK).u8(serial).zeros(3).toByteArray()

    fun privatePayload(cmdId: Int, data: ByteArray): ByteArray = ByteSink(data.size + 4).u16(cmdId).u16(data.size).bytes(data).toByteArray()

    fun isChunkMarker(b: Int) = b in CHUNK_MARKERS
    fun isFileChunk(b: Int) = b in FILE_CHUNK_MARKERS
}

enum class InboundKind { ACK, PACKET, FILE_PROGRESS, FILE }

class BerryInbound(
    val kind: InboundKind,
    val fromDevice: Boolean = true,
    val serial: Int = 0,
    val event: Int = 0,
    val type: Int = 0,
    val payload: ByteArray = ByteArray(0),
    val received: Int = 0,
    val total: Int = 0,
)

/** Reassembles Berry units from arbitrarily fragmented transport reads. Not thread-safe. */
class BerryStreamParser {
    private var buf = ByteArray(4096)
    private var start = 0
    private var end = 0
    private var fileType = 0
    private var fileTotal = -1L
    private var fileData = ByteSink(0)

    val isReceivingFile get() = fileTotal >= 0

    fun reset() { start = 0; end = 0; fileTotal = -1; fileData = ByteSink(0) }

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<BerryInbound> {
        append(bytes, count)
        val out = ArrayList<BerryInbound>(2)
        while (end > start) {
            val consumed = step(out)
            if (consumed < 0) break
            start += consumed
        }
        if (start == end) { start = 0; end = 0 }
        return out
    }

    private fun append(bytes: ByteArray, count: Int) {
        if (end + count > buf.size) {
            val live = end - start
            if (live + count <= buf.size && start > 0) {
                System.arraycopy(buf, start, buf, 0, live)
            } else {
                val grown = ByteArray(maxOf(buf.size * 2, live + count))
                System.arraycopy(buf, start, grown, 0, live)
                buf = grown
            }
            start = 0; end = live
        }
        System.arraycopy(bytes, 0, buf, end, count)
        end += count
    }

    private fun u8(i: Int) = buf[start + i].toInt() and 0xFF
    private fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
    private val avail get() = end - start

    private fun step(out: MutableList<BerryInbound>): Int {
        if (Bytes.startsWith(buf, BerryFrame.HEAD_ACK, start, end)) {
            if (avail < 8) return -1
            out.add(BerryInbound(InboundKind.ACK, serial = u8(4), event = u8(5)))
            return 8
        }
        val app = Bytes.startsWith(buf, BerryFrame.HEAD_APP, start, end)
        val device = Bytes.startsWith(buf, BerryFrame.HEAD_DEVICE, start, end)
        val file = Bytes.startsWith(buf, BerryFrame.HEAD_FILE, start, end)
        if (app || device || file) {
            if (avail < 9) return -1
            val length = u16(7)
            if (avail < 9 + length) return -1
            val payload = buf.copyOfRange(start + 9, start + 9 + length)
            if (file) {
                fileType = u8(6)
                fileTotal = Bytes.u32le(payload, 0)
                fileData = ByteSink(fileTotal.toInt().coerceIn(16, 4 shl 20) + 16).bytes(payload)
                emitFile(out)
            } else {
                out.add(BerryInbound(InboundKind.PACKET, device, u8(4), u8(5), u8(6), payload))
            }
            return 9 + length
        }
        if (avail >= 3 && u8(1) == 0x48 && u8(2) == 0x5A && BerryFrame.isChunkMarker(u8(0))) {
            if (avail < 6) return -1
            val length = u16(3)
            if (avail < 6 + length) return -1
            if (fileTotal >= 0 && BerryFrame.isFileChunk(u8(0))) {
                fileData.bytes(buf, start + 6, start + 6 + length)
                emitFile(out)
            }
            return 6 + length
        }
        if (avail < 4) return -1
        var next = end
        for (head in listOf(BerryFrame.HEAD_ACK, BerryFrame.HEAD_APP, BerryFrame.HEAD_DEVICE, BerryFrame.HEAD_FILE)) {
            val idx = Bytes.indexOf(head, buf, start + 1, end)
            if (idx in 0 until next) next = idx
        }
        if (next == end) return avail - 3
        return next - start
    }

    private fun emitFile(out: MutableList<BerryInbound>) {
        val received = maxOf(0, fileData.size - 12)
        val total = fileTotal.toInt()
        if (received >= total) {
            out.add(BerryInbound(InboundKind.FILE, type = fileType, payload = fileData.toByteArray(), received = received, total = total))
            fileTotal = -1
            fileData = ByteSink(0)
        } else {
            out.add(BerryInbound(InboundKind.FILE_PROGRESS, type = fileType, received = received, total = total))
        }
    }
}
