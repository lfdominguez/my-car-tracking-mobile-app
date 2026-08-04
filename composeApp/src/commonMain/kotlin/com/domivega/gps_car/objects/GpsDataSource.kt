package com.domivega.gps_car.objects

import com.domivega.gps_car.models.data.LocationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GpsDataSource {
    private val _locationFlow = MutableStateFlow(
        LocationData(latitude = 0.0, longitude = 0.0, accuracy = -1.0)
    )
    val locationFlow: StateFlow<LocationData> = _locationFlow.asStateFlow()

    fun updateLocation(newLocation: LocationData) {
        _locationFlow.value = newLocation
    }
}