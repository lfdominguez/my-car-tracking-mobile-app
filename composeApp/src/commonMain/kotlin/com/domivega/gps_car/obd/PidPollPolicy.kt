package com.domivega.gps_car.obd

object PidPollPolicy {
    /**
     * Last-good HOT PIDs older than this are not published to samples / UI.
     * HOT PIDs are polled every round (~1.5 s), so 10 s is ~7 missed rounds — tight
     * on purpose, because the parked/ghost-trip logic relies on live PIDs going
     * stale quickly.
     */
    const val MAX_AGE_MS: Long = 10_000L

    /**
     * Same for SLOW PIDs, which rotate through [ObdPollSchedule]'s per-round slice
     * and only refresh every ~4-5 rounds (~6 s measured on a cheap ELM327 clone).
     * Against the HOT 10 s budget that leaves less than one missed refresh of
     * margin, so a single timeout or NO DATA punched a 3-10 s hole in every
     * analytical metric. 30 s absorbs four consecutive missed refreshes and still
     * drops a genuinely dead sensor well inside the 90 s trip-end grace.
     */
    const val SLOW_MAX_AGE_MS: Long = 30_000L

    /**
     * Trip-critical live PIDs: a sustained miss must not keep the last decode.
     * Holding last-good RPM is what made parked ghost trips look like 704 RPM.
     */
    val DROP_ON_MISS: Set<String> = setOf("0c", "0d")

    /**
     * Consecutive misses before a [DROP_ON_MISS] PID loses its last-good value.
     * Dropping on the *first* miss meant one 4 s adapter timeout blanked the RPM
     * gauge mid-drive; the parked case it guards against still resolves inside
     * [MAX_AGE_MS] via [expireOlderThan].
     */
    const val DROP_ON_MISS_THRESHOLD: Int = 3

    /**
     * Genuinely session-scoped: the VW cluster odometer is read once, at session
     * start, and then advanced by Mode 01 PID 0x31 deltas rather than re-read, so an
     * age-based expiry would drop it from UI and uploads for no reason.
     *
     * It is read once because reaching it means switching the ELM away from engine
     * addressing to issue UDS $22, and that reliably breaks Mode 01 afterwards. Only
     * the odometer justifies that risk, which is why no other cluster value is here.
     */
    val SESSION_HOLD_KEYS: Set<String> = setOf(
        OdometerReading.UDS_KM_KEY.lowercase(),
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
     * Miss/timeout/NO DATA: drop last-good RPM/speed once [consecutiveMisses] has
     * reached [DROP_ON_MISS_THRESHOLD]; keep other PIDs until [expireOlderThan]
     * (a single coolant miss should not punch a hole).
     */
    fun afterMiss(
        previous: Map<String, Double>,
        pid: String,
        consecutiveMisses: Int = DROP_ON_MISS_THRESHOLD,
    ): Map<String, Double> {
        if (pid.lowercase() !in DROP_ON_MISS) return previous
        if (consecutiveMisses < DROP_ON_MISS_THRESHOLD) return previous
        if (previous.keys.none { it.equals(pid, ignoreCase = true) }) return previous
        return previous.filterKeys { !it.equals(pid, ignoreCase = true) }
    }

    /** @return the staleness budget for [pid] given the caller's SLOW tier membership. */
    fun maxAgeMsFor(
        pid: String,
        slowPids: Set<String>,
        maxAgeMs: Long = MAX_AGE_MS,
        slowMaxAgeMs: Long = SLOW_MAX_AGE_MS,
    ): Long = if (pid.lowercase() in slowPids) slowMaxAgeMs else maxAgeMs

    fun expireOlderThan(
        values: Map<String, Double>,
        lastSeenAtMs: Map<String, Long>,
        nowMs: Long,
        slowPids: Set<String> = emptySet(),
        maxAgeMs: Long = MAX_AGE_MS,
        slowMaxAgeMs: Long = SLOW_MAX_AGE_MS,
    ): Map<String, Double> {
        if (values.isEmpty()) return values
        return values.filterKeys { pid ->
            if (pid.lowercase() in SESSION_HOLD_KEYS) return@filterKeys true
            val seen = lastSeenAtMs[pid]
                ?: lastSeenAtMs[pid.lowercase()]
                ?: lastSeenAtMs.entries
                    .firstOrNull { it.key.equals(pid, ignoreCase = true) }
                    ?.value
            seen != null &&
                nowMs - seen < maxAgeMsFor(pid, slowPids, maxAgeMs, slowMaxAgeMs)
        }
    }

    /** Socket/session gone: drop every cached PID, including stale 0.0 speed. */
    fun afterLinkLost(previous: Map<String, Double>): Map<String, Double> {
        if (previous.isEmpty()) return previous
        return emptyMap()
    }
}
