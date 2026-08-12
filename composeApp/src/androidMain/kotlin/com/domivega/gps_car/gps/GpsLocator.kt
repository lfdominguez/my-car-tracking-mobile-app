package com.domivega.gps_car.gps

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat

/**
 * Platform location updates without Play Services.
 * API 31+: system fused when enabled; otherwise GPS.
 */
class GpsLocator(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListenerCompat? = null

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ],
    )
    fun start(onLocation: (Location) -> Unit) {
        stop()

        val fusedEnabled =
            sdkInt >= GpsProviderSelector.MIN_SDK_FUSED &&
                runCatching {
                    locationManager.isProviderEnabled(GpsProviderSelector.PROVIDER_FUSED)
                }.getOrDefault(false)
        val gpsEnabled =
            runCatching {
                locationManager.isProviderEnabled(GpsProviderSelector.PROVIDER_GPS)
            }.getOrDefault(false)
        val provider = GpsProviderSelector.select(sdkInt, fusedEnabled, gpsEnabled) ?: return

        val locationListener = LocationListenerCompat { location -> onLocation(location) }
        listener = locationListener

        val request = LocationRequestCompat.Builder(/* intervalMillis = */ 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        LocationManagerCompat.requestLocationUpdates(
            locationManager,
            provider,
            request,
            ContextCompat.getMainExecutor(appContext),
            locationListener,
        )
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        runCatching { LocationManagerCompat.removeUpdates(locationManager, current) }
    }
}
