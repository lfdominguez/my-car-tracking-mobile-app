package com.domivega.gps_car.models

import androidx.lifecycle.ViewModel
import com.domivega.gps_car.models.data.LocationData
import com.domivega.gps_car.objects.GpsDataSource
import kotlinx.coroutines.flow.StateFlow

class LocationViewModel(
    private val gpsDataSource: GpsDataSource // Inject the Android-specific data source
) : ViewModel() {

    val locationState: StateFlow<LocationData> = gpsDataSource.locationFlow
}