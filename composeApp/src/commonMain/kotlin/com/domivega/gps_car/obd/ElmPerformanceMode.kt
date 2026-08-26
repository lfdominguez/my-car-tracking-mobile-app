package com.domivega.gps_car.obd

/**
 * ELM327 Performance-mode command helpers.
 * Normal mode keeps ATAT1 and unsuffixed Mode 01 polls (adapter waits for timeout).
 * Performance uses ATAT2 and a trailing expected-line count for single-frame Mode 01 PIDs.
 */
object ElmPerformanceMode {
    fun adaptiveTimingCommand(performance: Boolean): String =
        if (performance) "ATAT2" else "ATAT1"

    fun mode01PollCommand(pidHex: String, performance: Boolean): String {
        val pid = pidHex.trim().lowercase()
        val base = "01" + pid.uppercase()
        if (!performance || !expectsSingleResponseLine(pid)) return base
        return base + "1"
    }

    fun expectsSingleResponseLine(pidHex: String): Boolean {
        val pid = pidHex.trim().lowercase()
        // Multi-frame payloads: 0xA6 is 4 data bytes plus header, 0x9A is 6, and both
        // land past the 7-byte single-frame limit once the 41xx marker is counted.
        if (pid == "a6" || pid == "9a") return false
        val n = pid.toIntOrNull(16) ?: return false
        // SAE J1979 support bitmaps: 00, 20, 40, 60, 80, A0, C0
        if (n % 0x20 == 0) return false
        return true
    }
}
