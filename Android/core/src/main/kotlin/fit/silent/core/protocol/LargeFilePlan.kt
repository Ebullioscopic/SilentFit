package fit.silent.core.protocol

/**
 * Splits an app → watch large file (watch face, album background, OTA) into wire chunks.
 *
 * Mirrors the vendor transfer (docs/PROTOCOL.md §7):
 * - payload = `u32 length | u32 fileType | u32 0 | file`
 * - chunk 0 = Berry frame (66 66 48 5A, event 2, type = fileType) carrying the first `slice − 9` payload bytes
 * - later chunks = raw payload slices of `slice` bytes
 * - the watch acknowledges (99 99 48 5A) roughly every 4096 bytes received
 */
class LargeFilePlan(val fileType: Int, file: ByteArray, expectedSlice: Int, firstSerial: Int) {
    val payload: ByteArray = ByteSink(file.size + 12).u32(file.size).u32(fileType).u32(0).bytes(file).toByteArray()
    val sliceLength: Int = sliceFor(expectedSlice)
    val chunks: List<ByteArray>
    /** Number of acknowledgement checkpoints the watch will send for the whole transfer. */
    val checkpoints: Int

    init {
        val list = ArrayList<ByteArray>()
        val firstData = minOf(sliceLength - BerryFrame.HEADER_SIZE, payload.size)
        list += BerryFrame.encode(firstSerial, BerryFrame.EVENT_LARGE_FILE, fileType, payload.copyOfRange(0, firstData))
        var at = firstData
        while (at < payload.size) {
            val end = minOf(at + sliceLength, payload.size)
            list += payload.copyOfRange(at, end)
            at = end
        }
        chunks = list
        val wire = chunks.sumOf { it.size.toLong() }
        checkpoints = ((wire + ACK_BLOCK - 1) / ACK_BLOCK).toInt()
    }

    val totalBytes get() = chunks.sumOf { it.size }

    companion object {
        const val SLICE_BASE = 990
        const val ACK_BLOCK = 4096
        const val DEFAULT_SLICE = 4950

        /** Smallest multiple of 990 strictly greater than the watch's expected slice length. */
        fun sliceFor(expected: Int): Int {
            if (expected <= 0 || expected > 1 shl 20) return DEFAULT_SLICE
            return (expected / SLICE_BASE + 1) * SLICE_BASE
        }
    }
}
