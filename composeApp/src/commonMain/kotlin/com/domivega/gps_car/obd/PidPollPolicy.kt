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
}
