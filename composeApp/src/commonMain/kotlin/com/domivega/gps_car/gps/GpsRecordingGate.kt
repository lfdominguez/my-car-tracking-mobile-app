package com.domivega.gps_car.gps

/**
 * Decides whether a *fresh* GPS fix may be attached to the current sample.
 *
 * Since sampling moved to a fixed 1 Hz clock this gate no longer decides whether a
 * sample exists — [SampleRecordingGate] does that, and engine telemetry is recorded
 * whether or not the car has a fix. What is left here is the parked hold: once OBD
 * has reported exactly 0.0 kph for longer than [DEFAULT_PARKED_HOLD_MS], GPS noise
 * must not grow the track, so the sample carries the frozen anchor from
 * [StationaryPositionFilter] instead of the incoming fix.
 *
 * The parked hold is deliberately not redundant with that filter. The filter freezes
 * on the **max** of OBD and GPS speed, so drift reported as a few km/h of GPS motion
 * unfreezes it; a decoded OBD 0.0 is the stronger signal that the car has not moved.
 */
object GpsRecordingGate {
    const val DEFAULT_PARKED_HOLD_MS: Long = 10_000L

    fun shouldAttachFreshFix(
        lastValidObdSpeedKph: Double?,
        speedZeroSinceMs: Long?,
        nowMs: Long,
        parkedHoldMs: Long = DEFAULT_PARKED_HOLD_MS,
    ): Boolean {
        if (lastValidObdSpeedKph != null && lastValidObdSpeedKph == 0.0) {
            val since = speedZeroSinceMs ?: return true
            if (parkedHoldMs <= 0L) return false
            return nowMs - since <= parkedHoldMs
        }
        // Unknown speed is not parked.
        return true
    }

    fun nextZeroSinceMs(
        previousZeroSinceMs: Long?,
        lastValidObdSpeedKph: Double?,
        nowMs: Long,
    ): Long? {
        if (lastValidObdSpeedKph == null || lastValidObdSpeedKph != 0.0) return null
        return previousZeroSinceMs ?: nowMs
    }
}
