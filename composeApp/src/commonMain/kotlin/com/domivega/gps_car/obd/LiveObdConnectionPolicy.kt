package com.domivega.gps_car.obd

/**
 * Pure transitions for ObdBleManager.ecuConnected from live OBD decodes.
 *
 * Live metrics (ICE + EV-friendly): RPM `0c`, speed `0d`, control-module voltage `42`.
 * ELM init or other PIDs alone must not open a trip. Miss streak is independent of
 * pidValues: RPM/speed are dropped on miss ([PidPollPolicy.afterMiss]).
 */
object LiveObdConnectionPolicy {
    const val DEFAULT_MISS_THRESHOLD: Int = 5

    /** PID hex keys that prove the vehicle ECU is answering usefully. */
    val LIVE_PIDS: Set<String> = setOf("0c", "0d", "42")

    data class State(
        val ecuConnected: Boolean,
        val consecutiveMisses: Int,
    )

    fun isLivePid(pid: String): Boolean =
        pid.lowercase() in LIVE_PIDS

    fun onLiveSuccess(
        currentlyConnected: Boolean,
        consecutiveMisses: Int,
    ): State = State(
        ecuConnected = true,
        consecutiveMisses = 0,
    )

    fun onLiveMiss(
        currentlyConnected: Boolean,
        consecutiveMisses: Int,
        threshold: Int = DEFAULT_MISS_THRESHOLD,
    ): State {
        val misses = consecutiveMisses + 1
        val connected = currentlyConnected && misses < threshold
        return State(ecuConnected = connected, consecutiveMisses = misses)
    }
}
