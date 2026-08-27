package com.domivega.gps_car.gps

/** Why a 1 Hz sample tick did or did not produce a sample. */
enum class SampleTickDecision {
    RECORD,
    PAUSED_OBD_DISCONNECTED,
    PAUSED_VEHICLE_OFF,
}

/**
 * Decides whether a sample clock tick may append a point to the current trip.
 *
 * Sampling is driven by a fixed 1 Hz clock rather than by GPS callbacks, so this
 * gate answers only "is a trip actually running?". Whether the tick carries
 * coordinates is a separate question owned by [GpsFixFreshness] and
 * [GpsRecordingGate] — engine telemetry is recorded either way.
 *
 * Trips stay ECU-driven: a closed Bluetooth link or a vehicle-off grace (the trip
 * stays open for 90s so EndTrip can be delayed) must not grow the track.
 */
object SampleRecordingGate {
    fun decide(ecuConnected: Boolean, vehicleOn: Boolean): SampleTickDecision = when {
        !ecuConnected -> SampleTickDecision.PAUSED_OBD_DISCONNECTED
        // The 90s grace only delays EndTrip; do not keep appending points.
        !vehicleOn -> SampleTickDecision.PAUSED_VEHICLE_OFF
        else -> SampleTickDecision.RECORD
    }

    fun shouldRecord(ecuConnected: Boolean, vehicleOn: Boolean): Boolean =
        decide(ecuConnected, vehicleOn) == SampleTickDecision.RECORD
}
