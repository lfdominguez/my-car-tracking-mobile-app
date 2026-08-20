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

    /**
     * One transition per poll round. Per-PID live misses (0c then 0d then 42)
     * used to flip [ecuConnected] false in a couple of seconds and punch holes
     * in uploaded tracks even while other PIDs still decoded.
     */
    fun onRoundEnd(
        currentlyConnected: Boolean,
        consecutiveMisses: Int,
        liveDecodedThisRound: Boolean,
        threshold: Int = DEFAULT_MISS_THRESHOLD,
    ): State {
        return if (liveDecodedThisRound) {
            onLiveSuccess(currentlyConnected, consecutiveMisses)
        } else {
            onLiveMiss(currentlyConnected, consecutiveMisses, threshold)
        }
    }

    /** Skip miss accounting for live PIDs the ECU never advertised. */
    fun shouldCountLiveMiss(
        pid: String,
        supportedMode01: Set<Int>,
    ): Boolean {
        if (!isLivePid(pid)) return false
        return PidSupport.isMode01Supported(supportedMode01, pid)
    }
}
