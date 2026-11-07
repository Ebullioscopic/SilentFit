package fit.silent.core.protocol

/**
 * Reads the watch's Bluetooth address from the QR code it shows for pairing.
 *
 * Accepts the forms NoiseFit 5.5.0 accepts (QrScanFragment): a `mac`, `MAC` or `m` query parameter, `g=<x>|<mac>`,
 * `<x>|<mac>`, and `… MAC:<mac>`; with or without colons.
 */
object WatchQr {
    private val HEX12 = Regex("(?i)(?<![0-9a-f])([0-9a-f]{2}[:-]?){5}[0-9a-f]{2}(?![0-9a-f])")

    fun address(scanned: String): String? {
        val text = scanned.replace("\u0000", "").trim()
        if (text.isEmpty()) return null
        val candidates = buildList {
            query(text, "mac")?.let(::add)
            query(text, "MAC")?.let(::add)
            query(text, "m")?.let(::add)
            query(text.replace('#', '?'), "g")?.split('|')?.getOrNull(1)?.let(::add)
            text.split('|').getOrNull(1)?.let(::add)
            Regex("(?i)MAC:\\s*([0-9a-f:\\-]{12,17})").find(text)?.groupValues?.get(1)?.let(::add)
            HEX12.find(text)?.value?.let(::add)
        }
        return candidates.firstNotNullOfOrNull(::normalize)
    }

    /** `AA:BB:CC:DD:EE:FF`, or null when [raw] is not a 48-bit address. */
    fun normalize(raw: String): String? {
        val hex = raw.trim().replace(":", "").replace("-", "").uppercase()
        if (hex.length != 12 || hex.any { it !in '0'..'9' && it !in 'A'..'F' } || hex == "000000000000") return null
        return hex.chunked(2).joinToString(":")
    }

    private fun query(text: String, key: String): String? {
        val q = text.substringAfter('?', "").ifEmpty { return null }
        return q.split('&').firstNotNullOfOrNull { pair ->
            val (k, v) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (k == key) java.net.URLDecoder.decode(v, "UTF-8").takeIf { it.isNotEmpty() } else null
        }
    }
}
