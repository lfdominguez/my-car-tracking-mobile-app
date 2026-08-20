package com.domivega.gps_car.obd

object PidPollPolicy {
    /** Last-good PIDs older than this are not published to samples / UI. */
    const val MAX_AGE_MS: Long = 10_000L

    /**
     * Trip-critical live PIDs: a miss must not keep the last decode.
     * Holding last-good RPM is what made parked ghost trips look like 704 RPM.
     */
    val DROP_ON_MISS: Set<String> = setOf("0c", "0d")

    /**
     * VW cluster UDS readings locked for the session. They are not Mode 01-polled
     * every round, so a 10s last-good expiry would drop odometer/oil from UI and
     * uploads after the pre-Mode 01 hop.
     */
    val SESSION_HOLD_KEYS: Set<String> = setOf(
        OdometerReading.UDS_KM_KEY.lowercase(),
        VwClusterDids.KEY_OIL_C.lowercase(),
        VwClusterDids.KEY_FUEL_PCT.lowercase(),
        VwClusterDids.KEY_DOORS.lowercase(),
    )

    fun afterSuccess(
        previous: Map<String, Double>,
        pid: String,
        value: Double,
    ): Map<String, Double> {
        val updated = LinkedHashMap(previous)
        updated[pid] = value
        return updated
    }

    /**
     * Miss/timeout/NO DATA: drop last-good RPM/speed; keep other PIDs until
     * [expireOlderThan] (a single coolant miss should not punch a hole).
     */
    fun afterMiss(
        previous: Map<String, Double>,
        pid: String,
    ): Map<String, Double> {
        if (pid.lowercase() !in DROP_ON_MISS) return previous
        if (previous.keys.none { it.equals(pid, ignoreCase = true) }) return previous
        return previous.filterKeys { !it.equals(pid, ignoreCase = true) }
    }

    fun expireOlderThan(
        values: Map<String, Double>,
        lastSeenAtMs: Map<String, Long>,
        nowMs: Long,
        maxAgeMs: Long = MAX_AGE_MS,
    ): Map<String, Double> {
        if (values.isEmpty()) return values
        return values.filterKeys { pid ->
            if (pid.lowercase() in SESSION_HOLD_KEYS) return@filterKeys true
            val seen = lastSeenAtMs[pid]
                ?: lastSeenAtMs[pid.lowercase()]
                ?: lastSeenAtMs.entries
                    .firstOrNull { it.key.equals(pid, ignoreCase = true) }
                    ?.value
            seen != null && nowMs - seen < maxAgeMs
        }
    }

    /** Socket/session gone: drop every cached PID, including stale 0.0 speed. */
    fun afterLinkLost(previous: Map<String, Double>): Map<String, Double> {
        if (previous.isEmpty()) return previous
        return emptyMap()
    }
}
