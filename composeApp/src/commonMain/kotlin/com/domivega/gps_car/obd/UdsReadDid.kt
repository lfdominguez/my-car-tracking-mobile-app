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
        0x22B0, // UNVERIFIED — often cited in community notes for cluster distance
        0x2203, // UNVERIFIED — MQB-related placeholder
        0x029F, // UNVERIFIED — alternate placeholder seen in public discussions
    )

    private const val SANE_KM_MAX = 2_000_000.0

    /**
     * Positive response payload bytes after `62 DID_H DID_L`, or null.
     */
    fun parsePositiveReadDid(raw: String, did: Int): ByteArray? {
        if (raw.isBlank()) return null
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("ERROR")) return null
        // Negative response: 7F <sid> <nrc>
        if (Regex("""7F\s*22""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return null

        val clean = raw
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")
            .uppercase()
            .filter { it in '0'..'9' || it in 'A'..'F' }

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
     * Decode odometer km from DID payload.
     *
     * Common layouts tried (deterministic order):
     * - 4-byte big-endian integer / 10.0 (0.1 km resolution), if result in 0..[SANE_KM_MAX]
     * - 4-byte big-endian integer as whole km, if in 0..[SANE_KM_MAX]
     * - 3-byte big-endian integer as whole km, if in 0..[SANE_KM_MAX]
     *
     * Prefer /10 for 4-byte values when that yields a sane reading (matches SAE A6 style).
     */
    fun decodeOdometerKm(payload: ByteArray): Double? {
        when (payload.size) {
            4 -> {
                val raw = payload.toUIntBe()
                val asTenths = raw / 10.0
                if (asTenths in 0.0..SANE_KM_MAX) return asTenths
                val asWhole = raw.toDouble()
                if (asWhole in 0.0..SANE_KM_MAX) return asWhole
                return null
            }
            3 -> {
                val raw = payload.toUIntBe()
                val asWhole = raw.toDouble()
                if (asWhole in 0.0..SANE_KM_MAX) return asWhole
                return null
            }
            else -> return null
        }
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
