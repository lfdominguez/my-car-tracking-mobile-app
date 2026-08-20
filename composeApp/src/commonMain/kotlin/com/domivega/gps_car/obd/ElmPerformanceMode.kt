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
        if (pid == "a6") return false
        val n = pid.toIntOrNull(16) ?: return false
        // SAE J1979 support bitmaps: 00, 20, 40, 60, 80, A0, C0
        if (n % 0x20 == 0) return false
        return true
    }
}
