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
    /**
     * @param supportedMode01 ECU-advertised PID bitmap; empty when discovery failed,
     *   in which case nothing is filtered on support and [sessionDisabled] is the
     *   only thing keeping dead PIDs out of the rotation.
     * @param sessionDisabled lowercase PIDs that never decoded this session (see
     *   ObdBleManager.SESSION_DISABLE_MISS_STREAK). Applied regardless of
     *   [supportedMode01] so a failed `0100` cannot leave the round full of PIDs
     *   that only ever time out.
     */
    fun pidsForRound(
        round: Int,
        hot: List<String>,
        slow: List<String>,
        slowEvery: Int = 5,
        supportedMode01: Set<Int> = emptySet(),
        sessionDisabled: Set<String> = emptySet(),
    ): List<String> {
        require(round >= 1) { "round must be >= 1" }
        require(slowEvery >= 1) { "slowEvery must be >= 1" }
        val scheduled = hot + slowSliceForRound(round, slow, slowEvery)
        val live = if (sessionDisabled.isEmpty()) {
            scheduled
        } else {
            scheduled.filter { it.lowercase() !in sessionDisabled }
        }
        if (supportedMode01.isEmpty()) return live
        return live.filter { PidSupport.isMode01Supported(supportedMode01, it) }
    }

    /**
     * Every round gets a slice of [chunkSize] slow PIDs, rotating through the
     * full list so each one refreshes at least once every [slowEvery] rounds —
     * same average cadence as before, without ever bursting the whole list at once.
     */
    private fun slowSliceForRound(round: Int, slow: List<String>, slowEvery: Int): List<String> {
        if (slow.isEmpty()) return emptyList()
        val chunkSize = min(slow.size, ceil(slow.size / slowEvery.toDouble()).toInt())
        val start = ((round - 1) * chunkSize) % slow.size
        return (0 until chunkSize).map { slow[(start + it) % slow.size] }
    }
}
