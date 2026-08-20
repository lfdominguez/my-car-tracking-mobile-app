package com.domivega.gps_car.obd

/**
 * Fixed-round OBD PID schedule: hot every round; slow every [slowEvery]th round.
 * [round] is 1-based (first poll cycle = 1).
 */
object ObdPollSchedule {
    fun pidsForRound(
        round: Int,
        hot: List<String>,
        slow: List<String>,
        slowEvery: Int = 5,
        supportedMode01: Set<Int> = emptySet(),
    ): List<String> {
        require(round >= 1) { "round must be >= 1" }
        require(slowEvery >= 1) { "slowEvery must be >= 1" }
        val scheduled = if (round % slowEvery == 0) hot + slow else hot
        if (supportedMode01.isEmpty()) return scheduled
        return scheduled.filter { PidSupport.isMode01Supported(supportedMode01, it) }
    }
}
