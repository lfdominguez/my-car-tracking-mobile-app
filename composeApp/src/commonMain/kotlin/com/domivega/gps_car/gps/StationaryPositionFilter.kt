package com.domivega.gps_car.gps

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class FilteredPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val frozen: Boolean,
)

/**
 * Freezes lat/lon/acc to the last accepted fix while effective speed is ≤ [stoppedMaxKph]
 * and the new fix is still near the anchor.
 *
 * Effective speed is the **max** of available OBD kph and GPS m/s→kph so a stale OBD 0
 * cannot pin the track while GPS reports motion. When both speeds are missing, never freezes
 * (pass-through).
 *
 * Even when speed says stopped, a large GPS jump from the anchor
 * (`distance > max([JUMP_MIN_METERS], accuracy × [JUMP_ACCURACY_FACTOR])`) unfreezes and
 * re-anchors so multi-km freeze→snap chords cannot form.
 */
class StationaryPositionFilter(
    private val stoppedMaxKph: Double = STOPPED_MAX_KPH,
) {
    companion object {
        const val STOPPED_MAX_KPH = 0.5
        const val JUMP_MIN_METERS = 50.0
        const val JUMP_ACCURACY_FACTOR = 3.0

        private const val EARTH_RADIUS_M = 6_371_000.0

        fun gpsSpeedToKph(speedMps: Double): Double = speedMps * 3.6

        /** Great-circle distance in meters (haversine). */
        fun distanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * asin(min(1.0, sqrt(a)))
            return EARTH_RADIUS_M * c
        }

        fun jumpThresholdMeters(accuracyMeters: Double): Double =
            max(JUMP_MIN_METERS, accuracyMeters * JUMP_ACCURACY_FACTOR)
    }

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastAccuracy: Double? = null

    fun effectiveSpeedKph(obdSpeedKph: Double?, gpsSpeedMps: Double?): Double? {
        val gpsKph = gpsSpeedMps?.let { gpsSpeedToKph(it) }
        return when {
            obdSpeedKph != null && gpsKph != null -> max(obdSpeedKph, gpsKph)
            obdSpeedKph != null -> obdSpeedKph
            gpsKph != null -> gpsKph
            else -> null
        }
    }

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

        val speedSaysStopped = effectiveKph != null && effectiveKph <= stoppedMaxKph
        if (speedSaysStopped &&
            anchorLat != null &&
            anchorLon != null &&
            anchorAcc != null
        ) {
            val jumpM = distanceMeters(anchorLat, anchorLon, latitude, longitude)
            val thresholdM = jumpThresholdMeters(accuracy)
            if (jumpM <= thresholdM) {
                return FilteredPosition(
                    latitude = anchorLat,
                    longitude = anchorLon,
                    accuracy = anchorAcc,
                    frozen = true,
                )
            }
            // Large jump while "stopped": accept current and re-anchor (unfreeze).
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
