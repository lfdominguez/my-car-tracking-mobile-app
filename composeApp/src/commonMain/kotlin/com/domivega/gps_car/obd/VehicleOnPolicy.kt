package com.domivega.gps_car.obd

/**
 * Whether the vehicle is "on" for trip start/stop — not merely that the ELM/ECU answers.
 *
 * Before this session has seen RPM > 0 (EV / voltage-only):
 *   proofs = RPM>0, speed>0, or a live voltage decode.
 * After RPM > 0 (ICE):
 *   proofs = RPM>0 or speed>0. Voltage and RPM=0 are not proofs.
 *
 * [lastProofAtMs] ages out after [DEFAULT_STALE_MS] so a hung adapter or
 * continuous RPM=0 drops [isVehicleOn] without waiting for the trip grace.
 */
object VehicleOnPolicy {
    const val DEFAULT_STALE_MS: Long = 10_000L

    data class State(
        val sawPositiveRpm: Boolean = false,
        val lastProofAtMs: Long? = null,
    )

    fun onRpm(state: State, rpm: Double, nowMs: Long): State {
        if (rpm > 0.0) {
            return State(sawPositiveRpm = true, lastProofAtMs = nowMs)
        }
        return state
    }

    fun onSpeed(state: State, speedKph: Double, nowMs: Long): State {
        if (speedKph > 0.0) {
            return state.copy(lastProofAtMs = nowMs)
        }
        return state
    }

    fun onVoltage(state: State, nowMs: Long): State {
        if (state.sawPositiveRpm) return state
        return state.copy(lastProofAtMs = nowMs)
    }

    fun isVehicleOn(
        state: State,
        nowMs: Long,
        staleMs: Long = DEFAULT_STALE_MS,
    ): Boolean {
        val at = state.lastProofAtMs ?: return false
        if (staleMs <= 0L) return true
        return nowMs - at < staleMs
    }

    /** Clear proof clock after a hard OBD session reset (not after EndTrip). */
    fun onSessionReset(sawPositiveRpm: Boolean): State =
        State(sawPositiveRpm = sawPositiveRpm, lastProofAtMs = null)

    fun shouldAcceptGpsSpeed(obdSpeedDecoded: Boolean): Boolean = !obdSpeedDecoded

    fun applyFreshPid(state: State, pid: String, value: Double, nowMs: Long): State =
        when (pid.lowercase()) {
            "0c" -> onRpm(state, value, nowMs)
            "0d" -> onSpeed(state, value, nowMs)
            "42" -> onVoltage(state, nowMs)
            else -> state
        }
}
