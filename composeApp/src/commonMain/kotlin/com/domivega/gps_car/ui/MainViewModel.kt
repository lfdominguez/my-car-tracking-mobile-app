package com.domivega.gps_car.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domivega.gps_car.data.CarMetricSource
import com.domivega.gps_car.data.queue.QueueHealthMessages
import com.domivega.gps_car.data.queue.UploadStatusDataSource
import com.domivega.gps_car.objects.GpsDataSource
import com.domivega.gps_car.obd.FuelLevelReading
import com.domivega.gps_car.obd.OdometerReading
import com.domivega.gps_car.obd.VwClusterDids
import com.domivega.gps_car.ui.state.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainViewModel(
    private val carMetricSource: CarMetricSource,
    private val gpsDataSource: GpsDataSource = GpsDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        combine(
            carMetricSource.pidValues,
            carMetricSource.ecuConnected,
            carMetricSource.serviceVersion,
            gpsDataSource.locationFlow,
            UploadStatusDataSource.status,
        ) { pidValues, ecuConnected, serviceVersion, location, uploadStatus ->
            val speed = pidValues["0d"] ?: 0.0
            val rpm = pidValues["0c"] ?: 0.0
            val engineLoad = pidValues["04"] ?: 0.0
            val fuelLevel = FuelLevelReading.fromPidValues(pidValues) ?: 0.0
            val odometerKm = OdometerReading.fromPidValues(pidValues)
            val oilTempC = pidValues[VwClusterDids.KEY_OIL_C]
            val doorsSummary = VwClusterDids.doorSummaryFromPidValues(pidValues)

            val isGpsLocked = location.accuracy != -1.0 && location.accuracy < 50.0

            val currentTracking = _uiState.value.isTracking
            val uploadWarning = QueueHealthMessages.warning(
                failedCount = uploadStatus.failedCount,
                deadCount = uploadStatus.deadCount,
                lastFlushOk = uploadStatus.lastFlushOk,
            )

            DashboardState(
                rpm = rpm,
                speed = speed,
                engineLoad = engineLoad,
                fuelLevel = fuelLevel,
                odometerKm = odometerKm,
                oilTempC = oilTempC,
                doorsSummary = doorsSummary,
                isTracking = currentTracking,
                isGpsLocked = isGpsLocked,
                ecuConnected = ecuConnected,
                uploadWarning = uploadWarning,
                serviceVersion = serviceVersion,
                pidValues = pidValues,
                pidNames = carMetricSource.pidNames,
            )
        }.onEach { newState ->
            _uiState.value = newState
        }.launchIn(viewModelScope)
    }

    fun updateTrackingState(isTracking: Boolean) {
        _uiState.value = _uiState.value.copy(isTracking = isTracking)
    }
}
