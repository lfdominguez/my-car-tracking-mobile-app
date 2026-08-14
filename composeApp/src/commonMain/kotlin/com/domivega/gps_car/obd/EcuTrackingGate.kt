package com.domivega.gps_car.obd

/**
 * Decides when ECU connect/disconnect should start/stop the tracking trip.
 *
 * Transient OBD blips (UDS header restore, short ELM re-init) must not end a trip.
 * Evaluation is level-based (not edge-only): tracking while already offline must arm
 * the disconnect grace even when there was no true→false transition observed.
 */
object EcuTrackingGate {
    /** Continuous ECU-off time before auto-stop ends the trip. */
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
     * Server `/start` bind only while the ECU is live — avoids ghost trips from GPS-only starts.
     * [ecuConnected] is RPM/speed/voltage-backed in ObdBleManager (not ELM init alone).
     */
    fun shouldBindServerSession(ecuConnected: Boolean): Boolean = ecuConnected

    /**
     * Pure reconcile of ECU + local tracking prefs.
     *
     * @param disconnectStartedAtMs prior armed timer, or null when not in disconnect grace.
     */
    fun evaluate(
        ecuConnected: Boolean,
        isTracking: Boolean,
        disconnectStartedAtMs: Long?,
        nowMs: Long,
        graceMs: Long = DEFAULT_STOP_GRACE_MS,
    ): EcuTrackingDecision {
        if (ecuConnected) {
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
