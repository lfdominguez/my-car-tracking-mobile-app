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
    ): List<String> {
        require(round >= 1) { "round must be >= 1" }
        require(slowEvery >= 1) { "slowEvery must be >= 1" }
        return if (round % slowEvery == 0) hot + slow else hot
    }
}
