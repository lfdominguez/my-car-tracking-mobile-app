package com.domivega.gps_car.obd

/**
 * Helpers for UDS ReadDataByIdentifier (service $22) responses from ELM327-style text.
 *
 * Positive response SID is $62; DID is two bytes. Payload layout for cluster odometer
 * is vendor-specific and must be confirmed on-vehicle.
 */
object UdsReadDid {

    /**
     * UNVERIFIED MQB / VAG cluster odometer DID placeholders for probe order only.
     * Replace / reorder after a successful road log; prefer settings override.
     */
    private val DEFAULT_CANDIDATE_DIDS: List<Int> = listOf(
        // Confirmed on VW Nivus Highline 2024 (MQB-A0 cluster) via road log.
        0x2203,
        0x22B0, // UNVERIFIED fallback — community notes for cluster distance
        0x029F, // UNVERIFIED — alternate placeholder seen in public discussions
    )

    private const val SANE_KM_MAX = 2_000_000.0

    /**
     * Positive response payload bytes after `62 DID_H DID_L`, or null.
     *
     * Handles single-line and multi-line ELM ISO-TP style replies where frame
     * indices (`0:`, `1:`) and a leading length line (`014`) must not be treated
     * as payload hex (naive strip-all-non-hex breaks DID markers).
     */
    fun parsePositiveReadDid(raw: String, did: Int): ByteArray? {
        if (raw.isBlank()) return null
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("ERROR")) return null
        // Negative response: 7F <sid> <nrc>
        if (Regex("""7F\s*22""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return null

        val clean = normalizeElmHex(raw)
        if (clean.length < 6) return null

        val didHi = (did shr 8) and 0xFF
        val didLo = did and 0xFF
        val marker = buildString {
            append("62")
            append(didHi.toString(16).padStart(2, '0').uppercase())
            append(didLo.toString(16).padStart(2, '0').uppercase())
        }

        val index = clean.indexOf(marker)
        if (index < 0) return null

        val dataHex = clean.substring(index + marker.length)
        if (dataHex.isEmpty() || dataHex.length % 2 != 0) return null

        return ByteArray(dataHex.length / 2) { i ->
            dataHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Reassemble ELM text into a continuous hex string for UDS parsing.
     *
     * Strips:
     * - `SEARCHING...` / prompt noise
     * - ISO-TP pretty-print frame markers (`0:`, `1:`, …) even when ATL0 glues
     *   multi-frame onto one line (`0140:622203…1:DA0E…`)
     * - a short leading length nibble run before the first `62` (e.g. `014`)
     *
     * Frame indices are recognized as 1–2 decimal digits immediately before `:`.
     * Longer digit runs before `:` keep the leading nibbles as hex payload
     * (so `…00031:` yields data `…0003` + frame `1:`, not a greedy `\d+:` eat).
     */
    internal fun normalizeElmHex(raw: String): String {
        val src = raw.uppercase().replace("SEARCHING", " ")
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '>' || c.isWhitespace() -> i++
                c in '0'..'9' -> {
                    var j = i
                    while (j < src.length && src[j] in '0'..'9') j++
                    if (j < src.length && src[j] == ':') {
                        val digitLen = j - i
                        // ELM frame index is 1–2 digits before ':'. Extra leading digits are hex.
                        val idxLen = if (digitLen <= 2) digitLen else 1
                        val dataEnd = j - idxLen
                        if (dataEnd > i) {
                            out.append(src, i, dataEnd)
                        }
                        i = j + 1 // skip index digits + ':'
                    } else {
                        out.append(src, i, j)
                        i = j
                    }
                }
                c in 'A'..'F' -> {
                    out.append(c)
                    i++
                }
                else -> i++ // punctuation, etc.
            }
        }

        val hex = out.toString()
        if (hex.length < 6) return hex

        // Drop ELM "014"-style length prefix immediately before SID 62 when present.
        val sid = hex.indexOf("62")
        if (sid in 1..3) {
            return hex.substring(sid)
        }
        return hex
    }

    /**
     * Decode odometer km from DID payload.
     *
     * Common layouts tried (deterministic order):
     * - first 4 bytes big-endian integer / 10.0 (0.1 km resolution), if result in 0..[SANE_KM_MAX]
     * - first 4 bytes big-endian integer as whole km, if in 0..[SANE_KM_MAX]
     * - first 3 bytes big-endian integer as whole km, if in 0..[SANE_KM_MAX]
     *
     * Longer payloads (padding / multi-frame tail) use the leading bytes only.
     */
    fun decodeOdometerKm(payload: ByteArray): Double? {
        if (payload.size >= 4) {
            val four = payload.copyOfRange(0, 4)
            val raw = four.toUIntBe()
            val asTenths = raw / 10.0
            if (asTenths in 0.0..SANE_KM_MAX) return asTenths
            val asWhole = raw.toDouble()
            if (asWhole in 0.0..SANE_KM_MAX) return asWhole
        }
        if (payload.size >= 3) {
            val three = payload.copyOfRange(0, 3)
            val raw = three.toUIntBe()
            val asWhole = raw.toDouble()
            if (asWhole in 0.0..SANE_KM_MAX) return asWhole
        }
        return null
    }

    /**
     * True when ELM text looks like an incomplete ISO-TP multi-frame (first frame /
     * length prefix present) rather than plain NO DATA — custom ATFC may help.
     *
     * Plain `NO DATA` must return false: forcing ATFCSM1 on many BLE ELM clones
     * breaks RX entirely (including a DID that worked without custom FC).
     */
    fun suggestsIsoTpFlowControlRetry(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val u = raw.uppercase()
        if (u.contains("NO DATA") || u.contains("ERROR") || u.contains("UNABLE")) {
            return false
        }
        if (u.contains("7F") && Regex("""7F\s*22""").containsMatchIn(u)) {
            return false
        }
        // Multi-frame pretty-print or length prefix without a full parseable UDS body.
        val hasFrameMarker = u.contains("0:")
        val hasLenPrefix = Regex("""(?m)^\s*0*[1-9A-F][0-9A-F]{0,2}\s*$""").containsMatchIn(raw) ||
            u.contains("014") || u.contains("013") || u.contains("012")
        val has62 = u.replace(" ", "").contains("62")
        if (!has62) return false
        // Incomplete if frame markers/length present but parse would need more CFs.
        return hasFrameMarker || hasLenPrefix
    }

    /**
     * If [overrideHex] is 4 hex chars (optional `0x` prefix), return that single DID.
     * Empty/null/invalid override → built-in UNVERIFIED candidate list.
     */
    fun candidateDids(overrideHex: String?): List<Int> {
        val trimmed = overrideHex?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_CANDIDATE_DIDS

        val hex = trimmed
            .removePrefix("0x")
            .removePrefix("0X")
            .trim()
        if (hex.length != 4) return DEFAULT_CANDIDATE_DIDS
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return DEFAULT_CANDIDATE_DIDS
        }
        return listOf(hex.toInt(16))
    }

    private fun ByteArray.toUIntBe(): Long {
        var value = 0L
        for (b in this) {
            value = (value shl 8) or (b.toInt() and 0xFF).toLong()
        }
        return value
    }
}
