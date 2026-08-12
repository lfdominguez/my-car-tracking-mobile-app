package com.domivega.gps_car.gps

/**
 * Chooses a platform LocationManager provider name without GMS.
 * API 31+ may use system fused; older APIs use GPS only.
 */
object GpsProviderSelector {
    const val PROVIDER_FUSED = "fused"
    const val PROVIDER_GPS = "gps"
    const val MIN_SDK_FUSED = 31

    fun select(sdkInt: Int, fusedEnabled: Boolean, gpsEnabled: Boolean): String? {
        if (sdkInt >= MIN_SDK_FUSED && fusedEnabled) return PROVIDER_FUSED
        if (gpsEnabled) return PROVIDER_GPS
        return null
    }
}
