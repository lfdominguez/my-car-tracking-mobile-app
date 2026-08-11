package com.domivega.gps_car.gps

data class FilteredPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val frozen: Boolean,
)

/**
 * Freezes lat/lon/acc to the last accepted fix while effective speed is ≤ [stoppedMaxKph].
 * Effective speed prefers OBD kph; falls back to GPS m/s → kph. When neither is available,
 * never freezes (pass-through) so missing speed cannot pin the map incorrectly.
 */
class StationaryPositionFilter(
    private val stoppedMaxKph: Double = STOPPED_MAX_KPH,
) {
    companion object {
        const val STOPPED_MAX_KPH = 0.5

        fun gpsSpeedToKph(speedMps: Double): Double = speedMps * 3.6
    }

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastAccuracy: Double? = null

    fun effectiveSpeedKph(obdSpeedKph: Double?, gpsSpeedMps: Double?): Double? =
        obdSpeedKph ?: gpsSpeedMps?.let { gpsSpeedToKph(it) }

    fun accept(
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        obdSpeedKph: Double?,
        gpsSpeedMps: Double?,
    ): FilteredPosition {
        val effectiveKph = effectiveSpeedKph(obdSpeedKph, gpsSpeedMps)
        val anchorLat = lastLatitude
        val anchorLon = lastLongitude
        val anchorAcc = lastAccuracy

        if (effectiveKph != null &&
            effectiveKph <= stoppedMaxKph &&
            anchorLat != null &&
            anchorLon != null &&
            anchorAcc != null
        ) {
            return FilteredPosition(
                latitude = anchorLat,
                longitude = anchorLon,
                accuracy = anchorAcc,
                frozen = true,
            )
        }

        lastLatitude = latitude
        lastLongitude = longitude
        lastAccuracy = accuracy
        return FilteredPosition(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            frozen = false,
        )
    }
}
