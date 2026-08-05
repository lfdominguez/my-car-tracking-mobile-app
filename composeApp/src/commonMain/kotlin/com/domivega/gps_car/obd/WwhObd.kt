package com.domivega.gps_car.obd

/**
 * WWH-OBD / OBDonUDS (SAE J1979-2 style) helpers: Mode 01 PID ↔ DID F4xx via UDS $22.
 * Scaling matches classic Mode 01; pair with [Elm327Parser] via [mode01CompatibleResponse].
 */
object WwhObd {
    fun didForPid(pid: Int): Int = 0xF400 or (pid and 0xFF)

    fun commandForPidHex(pidHex: String): String {
        val p = pidHex.trim().uppercase().padStart(2, '0')
        return "22F4$p"
    }

    private fun compact(raw: String): String =
        raw.uppercase()
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")

    fun isPositiveRead(raw: String?, expectPid: Int): Boolean {
        if (raw == null) return false
        val u = raw.uppercase()
        if (u.contains("NO DATA") || u.contains("UNABLE") || u.contains("ERROR")) return false
        if (compact(raw).contains("7F22")) return false
        val marker = "62F4" + expectPid.toString(16).uppercase().padStart(2, '0')
        return compact(raw).contains(marker)
    }

    fun dataHexAfterPositive(raw: String?, expectPid: Int): String? {
        if (!isPositiveRead(raw, expectPid)) return null
        val c = compact(raw!!)
        val marker = "62F4" + expectPid.toString(16).uppercase().padStart(2, '0')
        val idx = c.indexOf(marker)
        if (idx < 0) return null
        val data = c.substring(idx + marker.length).takeWhile { it in "0123456789ABCDEF" }
        return data.ifEmpty { null }
    }

    /** Build a synthetic Mode 01 positive frame so Elm327Parser can scale bytes. */
    fun mode01CompatibleResponse(pidHex: String, rawWwh: String?): String? {
        val pid = pidHex.trim().uppercase().padStart(2, '0')
        val pidInt = pid.toIntOrNull(16) ?: return null
        val data = dataHexAfterPositive(rawWwh, pidInt) ?: return null
        return "41$pid$data"
    }
}
