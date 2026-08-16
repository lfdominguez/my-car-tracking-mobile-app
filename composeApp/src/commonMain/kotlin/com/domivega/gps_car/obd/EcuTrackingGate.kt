package com.domivega.gps_car.obd

/**
 * Decides when vehicle-on proofs should start/stop the tracking trip.
 *
 * Transient OBD blips (UDS header restore, short ELM re-init) must not end a trip.
 * Evaluation is level-based (not edge-only): tracking while already offline must arm
 * the disconnect grace even when there was no true→false transition observed.
 *
 * [vehicleOn] is [VehicleOnPolicy] (RPM>0 / speed>0 / voltage-before-ICE), not
 * merely [LiveObdConnectionPolicy] ECU-answering.
 */
object EcuTrackingGate {
    /** Continuous vehicle-off time before auto-stop ends the trip. */
    const val DEFAULT_STOP_GRACE_MS: Long = 90_000L

    /**
     * @param disconnectStartedAtMs wall clock when continuous disconnect began, or null if connected.
     * @return true when tracking should stop for a sustained disconnect.
     */
    fun shouldStopForDisconnect(
        disconnectStartedAtMs: Long?,
        nowMs: Long,
        graceMs: Long = DEFAULT_STOP_GRACE_MS,
    ): Boolean {
        if (disconnectStartedAtMs == null) return false
        if (graceMs <= 0L) return true
        return nowMs - disconnectStartedAtMs >= graceMs
    }

    /**
     * Server `/start` bind only while the vehicle is on — avoids ghost trips from GPS-only starts.
     * [vehicleOn] is proof-backed in ObdBleManager (not ELM init or last-good PIDs).
     */
    fun shouldBindServerSession(vehicleOn: Boolean): Boolean = vehicleOn

    /**
     * Pure reconcile of vehicle-on + local tracking prefs.
     *
     * @param disconnectStartedAtMs prior armed timer, or null when not in disconnect grace.
     */
    fun evaluate(
        vehicleOn: Boolean,
        isTracking: Boolean,
        disconnectStartedAtMs: Long?,
        nowMs: Long,
        graceMs: Long = DEFAULT_STOP_GRACE_MS,
    ): EcuTrackingDecision {
        if (vehicleOn) {
            return EcuTrackingDecision(
                action = EcuTrackingAction.EnsureStart,
                disconnectStartedAtMs = null,
            )
        }

        if (!isTracking) {
            return EcuTrackingDecision(
                action = EcuTrackingAction.None,
                disconnectStartedAtMs = null,
            )
        }

        val armedAt = disconnectStartedAtMs ?: nowMs
        if (shouldStopForDisconnect(armedAt, nowMs, graceMs)) {
            return EcuTrackingDecision(
                action = EcuTrackingAction.EndTrip,
                disconnectStartedAtMs = null,
            )
        }
        return EcuTrackingDecision(
            action = EcuTrackingAction.None,
            disconnectStartedAtMs = armedAt,
        )
    }
}

enum class EcuTrackingAction {
    None,
    EnsureStart,
    /** End the local/server trip; keep WAITING FGS (do not full shutdown). */
    EndTrip,
}

data class EcuTrackingDecision(
    val action: EcuTrackingAction,
    val disconnectStartedAtMs: Long?,
)
