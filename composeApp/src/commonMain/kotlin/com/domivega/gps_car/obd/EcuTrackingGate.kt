package com.domivega.gps_car.obd

/**
 * Decides when ECU connect/disconnect should start/stop the tracking trip.
 *
 * Transient OBD blips (UDS header restore, short ELM re-init) must not end a trip.
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
}
