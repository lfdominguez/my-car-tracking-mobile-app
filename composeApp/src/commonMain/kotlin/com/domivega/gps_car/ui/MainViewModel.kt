package com.domivega.gps_car.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domivega.gps_car.data.CarMetricSource
import com.domivega.gps_car.objects.GpsDataSource
import com.domivega.gps_car.obd.OdometerReading
import com.domivega.gps_car.ui.state.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainViewModel(
    private val carMetricSource: CarMetricSource,
    private val gpsDataSource: GpsDataSource = GpsDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        // Collect metrics and update state
        // We combine the flows to produce a single UI state
        combine(
            carMetricSource.pidValues,
            carMetricSource.ecuConnected,
            carMetricSource.serviceVersion,
            gpsDataSource.locationFlow
        ) { pidValues, ecuConnected, serviceVersion, location ->
            
            // Mapping PID values to DashboardState properties
            // "0d" -> Vehicle Speed
            // "0c" -> Engine RPM
            // "04" -> Engine Load
            val speed = pidValues["0d"] ?: 0.0
            val rpm = pidValues["0c"] ?: 0.0
            val engineLoad = pidValues["04"] ?: 0.0
            val fuelLevel = pidValues["2f"] ?: 0.0
            val odometerKm = OdometerReading.fromPidValues(pidValues)
            
            // Heuristic for GPS Lock: if we have valid accuracy
            val isGpsLocked = location.accuracy != -1.0 && location.accuracy < 50.0 // Adjusted threshold
            
            // We preserve the current isTracking state as it is updated separately
            val currentTracking = _uiState.value.isTracking

            DashboardState(
                rpm = rpm,
                speed = speed,
                engineLoad = engineLoad,
                fuelLevel = fuelLevel,
                odometerKm = odometerKm,
                isTracking = currentTracking,
                isGpsLocked = isGpsLocked,
                ecuConnected = ecuConnected,
                serviceVersion = serviceVersion,
                pidValues = pidValues,
                pidNames = carMetricSource.pidNames
            )
        }.onEach { newState ->
            _uiState.value = newState
        }.launchIn(viewModelScope)
    }

    // Called by the platform (Activity/App) when the service state changes
    fun updateTrackingState(isTracking: Boolean) {
        _uiState.value = _uiState.value.copy(isTracking = isTracking)
    }
}
