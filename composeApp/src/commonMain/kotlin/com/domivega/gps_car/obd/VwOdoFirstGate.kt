package com.domivega.gps_car.obd

/**
 * VW MQB product rule: obtain cluster odometer before Mode 01 poll starts.
 * Non-VW profiles skip the gate.
 */
object VwOdoFirstGate {
    const val RETRY_DELAY_MS: Long = 2_000L

    fun requiresOdometerBeforeMode01(profile: VehicleObdProfile): Boolean =
        profile == VehicleObdProfile.VwMqb

    fun retryDelayMs(attemptIndex: Int): Long {
        @Suppress("UNUSED_PARAMETER")
        val ignored = attemptIndex.coerceAtLeast(0)
        return RETRY_DELAY_MS
    }
}
