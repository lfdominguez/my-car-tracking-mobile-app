package com.domivega.gps_car.obd

import kotlin.math.ceil
import kotlin.math.min

/**
 * Fixed-round OBD PID schedule: hot every round; slow PIDs rotate in small
 * per-round slices instead of landing in one large burst every [slowEvery]th
 * round. A round that suddenly triples in size (all slow PIDs at once) is what
 * caused cheap adapters to time out and let a just-refreshed slow value (e.g.
 * fuel level) sit unrefreshed long enough to expire from [PidPollPolicy].
 * [round] is 1-based (first poll cycle = 1).
 */
object ObdPollSchedule {
    /** Fraction of a PID's staleness budget after which it jumps the rotation. */
    const val DEFAULT_URGENT_FRACTION: Double = 0.5

    /** Ceiling on slow PIDs per round, so promotion can never burst the round. */
    const val DEFAULT_MAX_SLOW_PER_ROUND: Int = 6

    /**
     * @param supportedMode01 ECU-advertised PID bitmap; empty when discovery failed,
     *   in which case nothing is filtered on support and [sessionDisabled] is the
     *   only thing keeping dead PIDs out of the rotation.
     * @param sessionDisabled lowercase PIDs that never decoded this session (see
     *   ObdBleManager.SESSION_DISABLE_MISS_STREAK). Applied regardless of
     *   [supportedMode01] so a failed `0100` cannot leave the round full of PIDs
     *   that only ever time out.
     * @param lastSeenAtMs last successful decode per lowercase PID. Empty (the
     *   default) keeps the plain round-robin, so callers that cannot supply ages
     *   get exactly the previous behaviour.
     * @param slowBudgetMs staleness budget a slow PID is racing; a PID past
     *   [urgentFraction] of it is promoted ahead of the rotation.
     */
    fun pidsForRound(
        round: Int,
        hot: List<String>,
        slow: List<String>,
        slowEvery: Int = 5,
        supportedMode01: Set<Int> = emptySet(),
        sessionDisabled: Set<String> = emptySet(),
        lastSeenAtMs: Map<String, Long> = emptyMap(),
        nowMs: Long = 0L,
        slowBudgetMs: Long = PidPollPolicy.SLOW_MAX_AGE_MS,
        urgentFraction: Double = DEFAULT_URGENT_FRACTION,
        maxSlowPerRound: Int = DEFAULT_MAX_SLOW_PER_ROUND,
    ): List<String> {
        require(round >= 1) { "round must be >= 1" }
        require(slowEvery >= 1) { "slowEvery must be >= 1" }
        // Filter before slicing. Filtering afterwards spent a rotation slot on a
        // PID the ECU never advertised and then dropped it, so on a car with
        // several unsupported slow PIDs a fifth of the schedule polled nothing.
        val liveHot = filterLive(hot, supportedMode01, sessionDisabled)
        val liveSlow = filterLive(slow, supportedMode01, sessionDisabled)
        return liveHot + slowSliceForRound(
            round = round,
            slow = liveSlow,
            slowEvery = slowEvery,
            lastSeenAtMs = lastSeenAtMs,
            nowMs = nowMs,
            slowBudgetMs = slowBudgetMs,
            urgentFraction = urgentFraction,
            maxSlowPerRound = maxSlowPerRound,
        )
    }

    private fun filterLive(
        pids: List<String>,
        supportedMode01: Set<Int>,
        sessionDisabled: Set<String>,
    ): List<String> {
        val enabled = if (sessionDisabled.isEmpty()) {
            pids
        } else {
            pids.filter { it.lowercase() !in sessionDisabled }
        }
        if (supportedMode01.isEmpty()) return enabled
        return enabled.filter { PidSupport.isMode01Supported(supportedMode01, it) }
    }

    /**
     * Every round gets a slice of [chunkSize] slow PIDs, rotating through the
     * full list so each one refreshes at least once every [slowEvery] rounds —
     * same average cadence as before, without ever bursting the whole list at once.
     *
     * On top of that rotation, any PID already past [urgentFraction] of its
     * staleness budget is pulled to the front. One miss costs a slow PID a whole
     * rotation, so on a lossy adapter a PID can chain four misses and cross the
     * budget before the rotation comes back to it; promotion gives it another
     * attempt on the next round instead, at no extra command cost in steady state
     * (nothing is urgent while the rotation is keeping up).
     */
    private fun slowSliceForRound(
        round: Int,
        slow: List<String>,
        slowEvery: Int,
        lastSeenAtMs: Map<String, Long>,
        nowMs: Long,
        slowBudgetMs: Long,
        urgentFraction: Double,
        maxSlowPerRound: Int,
    ): List<String> {
        if (slow.isEmpty()) return emptyList()
        val chunkSize = min(slow.size, ceil(slow.size / slowEvery.toDouble()).toInt())
        val start = ((round - 1) * chunkSize) % slow.size
        val rotation = (0 until chunkSize).map { slow[(start + it) % slow.size] }
        if (lastSeenAtMs.isEmpty()) return rotation

        val urgentAfterMs = (slowBudgetMs * urgentFraction).toLong()
        val urgent = slow
            .filter { pid ->
                if (pid in rotation) return@filter false
                // Never decoded this session: the rotation is its only chance, and
                // promoting it would starve PIDs that are actually about to expire.
                val seen = lastSeenAtMs[pid.lowercase()] ?: return@filter false
                nowMs - seen >= urgentAfterMs
            }
            // Closest to expiry first; the list order breaks ties deterministically.
            .sortedByDescending { nowMs - (lastSeenAtMs[it.lowercase()] ?: 0L) }

        if (urgent.isEmpty()) return rotation
        val cap = maxSlowPerRound.coerceAtLeast(chunkSize)
        return (urgent + rotation).take(cap)
    }
}
