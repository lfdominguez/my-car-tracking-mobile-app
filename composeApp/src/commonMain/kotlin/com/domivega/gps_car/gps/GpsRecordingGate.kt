package com.domivega.gps_car.gps

/**
 * Decides whether a GPS fix may be appended to the current trip.
 *
 * OBD and GPS loops stay independent: this only snapshots connection,
 * vehicle-on, and last valid speed. A closed Bluetooth link, a vehicle-off
 * grace (trip still open for 90s), or a parked (exactly 0.0 kph) hold longer
 * than [DEFAULT_PARKED_HOLD_MS] must not grow the track.
 */
object GpsRecordingGate {
    const val DEFAULT_PARKED_HOLD_MS: Long = 10_000L

    fun shouldEnqueue(
        ecuConnected: Boolean,
        vehicleOn: Boolean,
        lastValidObdSpeedKph: Double?,
        speedZeroSinceMs: Long?,
        nowMs: Long,
        parkedHoldMs: Long = DEFAULT_PARKED_HOLD_MS,
    ): Boolean {
        if (!ecuConnected) return false
        // 90s grace only delays EndTrip; do not keep appending points.
        if (!vehicleOn) return false
        if (lastValidObdSpeedKph != null && lastValidObdSpeedKph == 0.0) {
            val since = speedZeroSinceMs ?: return true
            if (parkedHoldMs <= 0L) return false
            return nowMs - since <= parkedHoldMs
        }
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
