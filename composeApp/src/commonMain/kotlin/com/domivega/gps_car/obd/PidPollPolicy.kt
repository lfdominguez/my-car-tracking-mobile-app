package com.domivega.gps_car.obd

object PidPollPolicy {
    fun afterSuccess(
        previous: Map<String, Double>,
        pid: String,
        value: Double,
    ): Map<String, Double> {
        val updated = LinkedHashMap(previous)
        updated[pid] = value
        return updated
    }

    /** Miss/timeout/NO DATA: never remove last-good values. */
    fun afterMiss(
        previous: Map<String, Double>,
        pid: String,
    ): Map<String, Double> = previous

    /** Socket/session gone: drop every cached PID, including stale 0.0 speed. */
    fun afterLinkLost(previous: Map<String, Double>): Map<String, Double> {
        if (previous.isEmpty()) return previous
        return emptyMap()
    }
}
