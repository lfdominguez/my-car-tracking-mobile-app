package com.domivega.gps_car.obd

/**
 * Whether the vehicle is "on" for trip start/stop — not merely that the ELM/ECU answers.
 *
 * Powertrain ([PowertrainKind]) changes the rules:
 * - ICE (default): after RPM > 0, proofs = charging RPM, speed>0, or rising 1F.
 *   Rest-battery voltage with speed 0 is parked even if RPM still looks idle.
 * - Hybrid / Electric: RPM may be 0 while the vehicle is still on.
 *   Voltage and speed remain keep-alives. RPM>0 is a proof but does not lock
 *   into ICE parked-rest rules.
 *
 * Before this session has seen RPM > 0 (EV / voltage-only / hybrid EV mode):
 *   proofs = RPM>0, speed>0, or a live voltage decode.
 *
 * Cranking after a previous ICE trip looks like parked rest for a few seconds
 * (alternator has not raised voltage yet). Adapter sleep therefore waits
 * [PARKED_CONFIRM_MS] of continuous parked rest, and unknown speed is not parked.
 *
 * [lastProofAtMs] ages out after [DEFAULT_STALE_MS] so a hung adapter or
 * continuous RPM=0 drops [isVehicleOn] without waiting for the trip grace.
 */
enum class PowertrainKind {
    ICE,
    HYBRID,
    ELECTRIC,
}

object VehicleOnPolicy {
    const val DEFAULT_STALE_MS: Long = 10_000L

    /** Alternator / running electrical system. */
    const val CHARGING_VOLTAGE: Double = 13.2

    /** Open-circuit / parked battery. Observed ghosts sat at 12.49 V. */
    const val REST_VOLTAGE: Double = 12.8

    /** Hold parked-rest before sleeping the adapter so cranking is not "parked". */
    const val PARKED_CONFIRM_MS: Long = 20_000L

    data class State(
        val sawPositiveRpm: Boolean = false,
        val lastProofAtMs: Long? = null,
        val lastVoltage: Double? = null,
        val lastSpeedKph: Double? = null,
        val lastRpm: Double? = null,
        val lastRuntimeSec: Double? = null,
        val parkedRestSinceMs: Long? = null,
    )

    fun isRestVoltage(voltage: Double?): Boolean =
        voltage != null && voltage <= REST_VOLTAGE

    fun isChargingVoltage(voltage: Double?): Boolean =
        voltage != null && voltage >= CHARGING_VOLTAGE

    fun onRpm(
        state: State,
        rpm: Double,
        nowMs: Long,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): State {
        if (powertrain == PowertrainKind.ELECTRIC) {
            return withParkedRestClock(state.copy(lastRpm = rpm), nowMs, powertrain)
        }
        if (rpm <= 0.0) {
            return withParkedRestClock(state.copy(lastRpm = rpm), nowMs, powertrain)
        }
        if (powertrain == PowertrainKind.HYBRID) {
            return withParkedRestClock(
                state.copy(lastRpm = rpm, lastProofAtMs = nowMs),
                nowMs,
                powertrain,
            )
        }
        val wasIce = state.sawPositiveRpm
        val next = state.copy(sawPositiveRpm = true, lastRpm = rpm)
        if (!wasIce) {
            return withParkedRestClock(next.copy(lastProofAtMs = nowMs), nowMs, powertrain)
        }
        return withParkedRestClock(
            when {
                isChargingVoltage(next.lastVoltage) -> next.copy(lastProofAtMs = nowMs)
                isParkedRest(next) -> next.copy(lastProofAtMs = null)
                else -> next
            },
            nowMs,
            powertrain,
        )
    }

    fun onSpeed(
        state: State,
        speedKph: Double,
        nowMs: Long,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): State {
        val next = state.copy(lastSpeedKph = speedKph)
        if (speedKph > 0.0) {
            return withParkedRestClock(next.copy(lastProofAtMs = nowMs), nowMs, powertrain)
        }
        return withParkedRestClock(next, nowMs, powertrain)
    }

    fun onVoltage(
        state: State,
        volts: Double,
        nowMs: Long,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): State {
        val next = state.copy(lastVoltage = volts)
        if (powertrain != PowertrainKind.ICE || !next.sawPositiveRpm) {
            return withParkedRestClock(next.copy(lastProofAtMs = nowMs), nowMs, powertrain)
        }
        // After ICE, voltage is never a keep-alive — only a parked-off detector.
        return withParkedRestClock(
            if (isParkedRest(next)) next.copy(lastProofAtMs = null) else next,
            nowMs,
            powertrain,
        )
    }

    /**
     * Mode 01 PID 1F (seconds since engine start). A rising value is a live
     * engine-on proof even after ICE; a constant leftover from the last trip is not.
     */
    fun onRuntime(
        state: State,
        seconds: Double,
        nowMs: Long,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): State {
        val prev = state.lastRuntimeSec
        val next = state.copy(lastRuntimeSec = seconds)
        return if (prev != null && seconds > prev) {
            withParkedRestClock(next.copy(lastProofAtMs = nowMs), nowMs, powertrain)
        } else {
            withParkedRestClock(next, nowMs, powertrain)
        }
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
     * drop the Bluetooth link so the dongle can sleep, but only after the
     * parked-rest picture has held long enough to outlast cranking.
     */
    fun shouldReleaseAdapter(
        state: State,
        nowMs: Long,
        confirmMs: Long = PARKED_CONFIRM_MS,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): Boolean {
        val since = state.parkedRestSinceMs ?: return false
        if (powertrain != PowertrainKind.ICE) {
            return isParkedRest(state) && nowMs - since >= confirmMs
        }
        val rpm = state.lastRpm ?: return false
        return state.sawPositiveRpm &&
            rpm > 0.0 &&
            isParkedRest(state) &&
            nowMs - since >= confirmMs
    }

    /** Clear proof clock after a hard OBD session reset (not after EndTrip). */
    fun onSessionReset(sawPositiveRpm: Boolean): State =
        State(sawPositiveRpm = sawPositiveRpm, lastProofAtMs = null)

    /** Bluetooth/link drop: vehicle is immediately off; keep ICE flag. */
    fun onLinkLost(state: State): State =
        state.copy(lastProofAtMs = null, parkedRestSinceMs = null)

    fun shouldAcceptGpsSpeed(
        obdSpeedDecoded: Boolean,
        ecuConnected: Boolean,
    ): Boolean = !obdSpeedDecoded && ecuConnected

    fun applyFreshPid(
        state: State,
        pid: String,
        value: Double,
        nowMs: Long,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): State =
        when (pid.lowercase()) {
            "0c" -> onRpm(state, value, nowMs, powertrain)
            "0d" -> onSpeed(state, value, nowMs, powertrain)
            "42" -> onVoltage(state, value, nowMs, powertrain)
            "1f" -> onRuntime(state, value, nowMs, powertrain)
            "5b" -> if (powertrain != PowertrainKind.ICE && value > 0.0) {
                onVoltage(state, state.lastVoltage ?: CHARGING_VOLTAGE, nowMs, powertrain)
            } else {
                state
            }
            else -> state
        }

    private fun isParkedRest(state: State): Boolean {
        if (!isRestVoltage(state.lastVoltage)) return false
        return state.lastSpeedKph == 0.0
    }

    private fun hasFreshProof(state: State, nowMs: Long): Boolean {
        val at = state.lastProofAtMs ?: return false
        return nowMs - at < DEFAULT_STALE_MS
    }

    private fun withParkedRestClock(
        state: State,
        nowMs: Long,
        powertrain: PowertrainKind = PowertrainKind.ICE,
    ): State {
        val parked = if (powertrain != PowertrainKind.ICE) {
            !hasFreshProof(state, nowMs) && isParkedRest(state)
        } else {
            !hasFreshProof(state, nowMs) &&
                state.sawPositiveRpm &&
                (state.lastRpm ?: 0.0) > 0.0 &&
                isParkedRest(state)
        }
        return if (parked) {
            state.copy(parkedRestSinceMs = state.parkedRestSinceMs ?: nowMs)
        } else {
            state.copy(parkedRestSinceMs = null)
        }
    }
}
