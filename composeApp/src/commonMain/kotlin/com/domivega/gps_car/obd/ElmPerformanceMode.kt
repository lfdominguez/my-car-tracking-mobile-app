package com.domivega.gps_car.obd

/**
 * ELM327 timing and response-shaping commands.
 *
 * Performance mode is the user-facing opt-in: it swaps ATAT1 for the aggressive
 * ATAT2. The expected-response-count suffix is *not* tied to it — it is applied
 * whenever the session polls a single responder (a physical header such as 7E0),
 * where asking for one line is correct by construction.
 */
object ElmPerformanceMode {
    /**
     * ELM327 `ST` timer, in units of 4 ms, as the two hex digits `ATST` expects.
     *
     * `ST` is how long the adapter waits for the ECU before answering `NO DATA`.
     * It was never sent at all, so every session inherited whatever the clone had
     * saved — nominally 0x32, but not guaranteed. Sending it makes the timing
     * deterministic instead of adapter-dependent.
     */
    const val ST_BASELINE_HEX: String = "32" // 50 → 200 ms, the documented ELM default

    /**
     * Ceiling once polling is pinned to a physical header. Costs nothing in the
     * common case: with [mode01PollCommand]'s count suffix the adapter returns on
     * the first frame instead of waiting the window out, so the longer timer is
     * only ever paid by a PID that genuinely has no answer.
     */
    const val ST_SINGLE_RESPONDER_HEX: String = "96" // 150 → 600 ms

    /**
     * Ceiling while still on the functional header. Lower than
     * [ST_SINGLE_RESPONDER_HEX] because without the count suffix the adapter waits
     * the full window after *every* reply, looking for a second responder.
     */
    const val ST_FUNCTIONAL_HEX: String = "64" // 100 → 400 ms

    fun adaptiveTimingCommand(performance: Boolean): String =
        if (performance) "ATAT2" else "ATAT1"

    /** Disables adaptive timing so the adapter always waits the full `ST` window. */
    const val FIXED_TIMING_COMMAND: String = "ATAT0"

    /** Hot-PID miss share, in percent, that triggers [FIXED_TIMING_COMMAND]. */
    const val FIXED_TIMING_MISS_PCT: Int = 15

    /** Minimum hot-PID requests in a window before the miss share is trusted. */
    const val FIXED_TIMING_MIN_SAMPLES: Int = 40

    /**
     * True when ATAT1 looks like it is cutting the ECU off. Adaptive timing shortens
     * the wait below `ST` from recent reply times, so an ECU that is sometimes slower
     * reads as `NO DATA`: a real trip missed ~20% of RPM/speed requests, every minute,
     * on one car. Only on a single responder, where the count suffix returns on the
     * first frame and the full window is paid by misses alone, and never over the
     * user's opt-in to ATAT2.
     */
    fun shouldUseFixedTiming(
        hotOk: Int,
        hotMiss: Int,
        performance: Boolean,
        singleResponder: Boolean,
    ): Boolean {
        if (performance || !singleResponder) return false
        val total = hotOk + hotMiss
        if (total < FIXED_TIMING_MIN_SAMPLES) return false
        return hotMiss * 100 >= total * FIXED_TIMING_MISS_PCT
    }

    fun responseTimeoutCommand(stHex: String): String = "ATST${stHex.trim().uppercase()}"

    /**
     * @param singleResponder true when the session polls one module (physical
     * header), so a "stop after 1 line" hint cannot drop another ECU's reply.
     */
    fun mode01PollCommand(
        pidHex: String,
        performance: Boolean,
        singleResponder: Boolean = false,
    ): String {
        val pid = pidHex.trim().lowercase()
        val base = "01" + pid.uppercase()
        if (!performance && !singleResponder) return base
        if (!expectsSingleResponseLine(pid)) return base
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
