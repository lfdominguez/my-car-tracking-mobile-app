package com.domivega.gps_car.obd

/**
 * SAE J1979 Mode 01 PID support bitmaps (responses to 0100 / 0120 / 0140 / …).
 */
object PidSupport {
    private val HEX_PAIR = Regex("[0-9A-Fa-f]{2}")

    /**
     * @param supportCommandPid the PID used to query the bitmap (0x00, 0x20, 0x40, …)
     * @return set of supported Mode 01 PIDs in that block (e.g. 0x01..0x20 for 0x00)
     */
    fun parseSupportBitmap(supportCommandPid: Int, rawResponse: String): Set<Int> {
        if (rawResponse.isBlank()) return emptySet()
        if (rawResponse.contains("NO DATA", ignoreCase = true)) return emptySet()
        if (rawResponse.contains("UNABLE TO CONNECT", ignoreCase = true)) return emptySet()

        val bytes = extractDataBytes(supportCommandPid, rawResponse) ?: return emptySet()
        if (bytes.size < 4) return emptySet()

        val supported = linkedSetOf<Int>()
        var offset = 1
        for (i in 0 until 4) {
            val b = bytes[i]
            for (bit in 7 downTo 0) {
                if ((b shr bit) and 1 == 1) {
                    supported.add(supportCommandPid + offset)
                }
                offset += 1
            }
        }
        return supported
    }

    /** Merge multiple support-query responses into one PID set. */
    fun mergeBitmaps(vararg pairs: Pair<Int, String>): Set<Int> {
        val out = linkedSetOf<Int>()
        for ((cmdPid, raw) in pairs) {
            out += parseSupportBitmap(cmdPid, raw)
        }
        return out
    }

    /**
     * Whether [pidHex] (e.g. "0C", "a6") is listed as supported.
     * Non-standard / manufacturer PIDs (length != 2 hex) are treated as "unknown" → [unknownDefault].
     *
     * A support page that was advertised (next-page bit) but never successfully
     * fetched is incomplete: PIDs on that page and later stay [unknownDefault]
     * so a failed `0120` does not hide accelerator pedal (`49`) or voltage (`42`).
     */
    fun isMode01Supported(
        supported: Set<Int>,
        pidHex: String,
        unknownDefault: Boolean = true,
    ): Boolean {
        val normalized = pidHex.trim().uppercase().removePrefix("01")
        if (normalized.length != 2 || !normalized.all { it in "0123456789ABCDEF" }) {
            return unknownDefault
        }
        if (supported.isEmpty()) return true // no discovery yet → allow poll
        val pid = normalized.toInt(16)
        if (pid in supported) return true

        var page = 0
        while (page <= 0xE0) {
            val pageEnd = page + 0x20
            if (pid <= pageEnd) {
                val populated = supported.any { it in (page + 1)..pageEnd }
                return if (populated) false else unknownDefault
            }
            if (pageEnd !in supported) return false
            val nextPopulated = supported.any { it in (pageEnd + 1)..(pageEnd + 0x20) }
            if (!nextPopulated) return unknownDefault
            page += 0x20
        }
        return unknownDefault
    }

    /**
     * Next support-query PID to issue after a successful bitmap (0x20, 0x40, …), or null if done.
     * J1979: if the last bit of the bitmap (PID supportCommand+0x20) is set, more PIDs exist.
     */
    fun nextSupportCommandPid(supportCommandPid: Int, rawResponse: String): Int? {
        val supported = parseSupportBitmap(supportCommandPid, rawResponse)
        val moreBitPid = supportCommandPid + 0x20
        return if (moreBitPid in supported) moreBitPid else null
    }

    private fun extractDataBytes(supportCommandPid: Int, rawResponse: String): List<Int>? {
        val clean = rawResponse
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")
            .uppercase()
        val marker = "41" + supportCommandPid.toString(16).uppercase().padStart(2, '0')
        val idx = clean.indexOf(marker)
        if (idx < 0) return null
        val data = clean.substring(idx + marker.length)
        val pairs = HEX_PAIR.findAll(data).map { it.value.toInt(16) }.toList()
        return if (pairs.size >= 4) pairs.take(4) else null
    }
}
