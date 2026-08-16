package com.domivega.gps_car.obd

/**
 * Persistence rules for [VehicleOnPolicy.State.sawPositiveRpm].
 * EndTrip must not clear the flag (parked voltage would open a new trip).
 */
object VehicleOnPersist {
    const val PREFS_NAME: String = "tracking_prefs"
    const val KEY_SAW_POSITIVE_RPM: String = "vehicle_on_saw_positive_rpm"
    const val KEY_FLAG_DONGLE: String = "vehicle_on_flag_dongle"

    fun flagAfterTripEnd(sawPositiveRpm: Boolean): Boolean = sawPositiveRpm

    fun flagAfterShutdown(): Boolean = false

    fun shouldKeepFlag(previousAddress: String, newAddress: String): Boolean {
        if (previousAddress.isBlank()) return true
        return previousAddress == newAddress
    }
}
