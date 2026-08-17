package com.domivega.gps_car.obd

/**
 * Whether the vehicle is "on" for trip start/stop — not merely that the ELM/ECU answers.
 *
 * Before this session has seen RPM > 0 (EV / voltage-only):
 *   proofs = RPM>0, speed>0, or a live voltage decode.
 * After RPM > 0 (ICE):
 *   proofs = RPM>0 while the electrical system is charging, or speed>0.
 *   Rest-battery voltage (~12.5 V) with speed 0 is engine-off even if the ECU
 *   still publishes a stale idle RPM (parked ghosts).
 *
 * [lastProofAtMs] ages out after [DEFAULT_STALE_MS] so a hung adapter or
 * continuous RPM=0 drops [isVehicleOn] without waiting for the trip grace.
 */
object VehicleOnPolicy {
    const val DEFAULT_STALE_MS: Long = 10_000L

    /** Alternator / running electrical system. */
    const val CHARGING_VOLTAGE: Double = 13.2

    /** Open-circuit / parked battery. Observed ghosts sat at 12.49 V. */
    const val REST_VOLTAGE: Double = 12.8

    data class State(
        val sawPositiveRpm: Boolean = false,
        val lastProofAtMs: Long? = null,
        val lastVoltage: Double? = null,
        val lastSpeedKph: Double? = null,
        val lastRpm: Double? = null,
    )

    fun isRestVoltage(voltage: Double?): Boolean =
        voltage != null && voltage <= REST_VOLTAGE

    fun isChargingVoltage(voltage: Double?): Boolean =
        voltage != null && voltage >= CHARGING_VOLTAGE

    fun onRpm(state: State, rpm: Double, nowMs: Long): State {
        if (rpm <= 0.0) {
            return state.copy(lastRpm = rpm)
        }
        val wasIce = state.sawPositiveRpm
        val next = state.copy(sawPositiveRpm = true, lastRpm = rpm)
        if (!wasIce) {
            return next.copy(lastProofAtMs = nowMs)
        }
        return when {
            isChargingVoltage(next.lastVoltage) -> next.copy(lastProofAtMs = nowMs)
            isParkedRest(next) -> next.copy(lastProofAtMs = null)
            else -> next
        }
    }

    fun onSpeed(state: State, speedKph: Double, nowMs: Long): State {
        val next = state.copy(lastSpeedKph = speedKph)
        if (speedKph > 0.0) {
            return next.copy(lastProofAtMs = nowMs)
        }
        return next
    }

    fun onVoltage(state: State, volts: Double, nowMs: Long): State {
        val next = state.copy(lastVoltage = volts)
        if (!next.sawPositiveRpm) {
            return next.copy(lastProofAtMs = nowMs)
        }
        // After ICE, voltage is never a keep-alive — only a parked-off detector.
        return if (isParkedRest(next)) next.copy(lastProofAtMs = null) else next
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

    /**
     * ECU still answers with idle-like RPM on a rest battery after ICE —
     * drop the Bluetooth link so the dongle can sleep.
     */
    fun shouldReleaseAdapter(state: State): Boolean {
        val rpm = state.lastRpm ?: return false
        return state.sawPositiveRpm && rpm > 0.0 && isParkedRest(state)
    }

    /** Clear proof clock after a hard OBD session reset (not after EndTrip). */
    fun onSessionReset(sawPositiveRpm: Boolean): State =
        State(sawPositiveRpm = sawPositiveRpm, lastProofAtMs = null)

    /** Bluetooth/link drop: vehicle is immediately off; keep ICE flag. */
    fun onLinkLost(state: State): State =
        state.copy(lastProofAtMs = null)

    fun shouldAcceptGpsSpeed(
        obdSpeedDecoded: Boolean,
        ecuConnected: Boolean,
    ): Boolean = !obdSpeedDecoded && ecuConnected

    fun applyFreshPid(state: State, pid: String, value: Double, nowMs: Long): State =
        when (pid.lowercase()) {
            "0c" -> onRpm(state, value, nowMs)
            "0d" -> onSpeed(state, value, nowMs)
            "42" -> onVoltage(state, value, nowMs)
            else -> state
        }

    private fun isParkedRest(state: State): Boolean {
        if (!isRestVoltage(state.lastVoltage)) return false
        val speed = state.lastSpeedKph
        return speed == null || speed == 0.0
    }
}
