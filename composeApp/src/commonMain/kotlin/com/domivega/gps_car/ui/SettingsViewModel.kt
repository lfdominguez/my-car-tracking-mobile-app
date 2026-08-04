package com.domivega.gps_car.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domivega.gps_car.data.BackendConnectionTester
import com.domivega.gps_car.data.ConnectionTestOutcome
import com.domivega.gps_car.data.SettingsRepository
import com.domivega.gps_car.fuel.FuelTypePreset
import com.domivega.gps_car.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val connectionTester: BackendConnectionTester? = null,
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                apiToken = repository.apiToken,
                startUrl = repository.startUrl,
                stopUrl = repository.stopUrl,
                sampleUrl = repository.sampleUrl,
                samplesUrl = repository.samplesUrl,
                carId = repository.carId,
                carName = repository.carName,
                bleDeviceAddress = repository.bleDeviceAddress,
                bleDeviceName = repository.bleDeviceName,
                obdProtocol = repository.obdProtocol,
                fuelType = repository.fuelType,
                fuelStoichAfr = repository.fuelStoichAfr,
                fuelDensityGl = repository.fuelDensityGl,
                engineDisplacementL = repository.engineDisplacementL,
                engineVe = repository.engineVe,
            )
        }
    }

    fun updateApiToken(newValue: String) {
        repository.apiToken = newValue
        _uiState.update { it.copy(apiToken = newValue) }
    }

    fun updateStartUrl(newValue: String) {
        repository.startUrl = newValue
        _uiState.update { it.copy(startUrl = newValue) }
    }

    fun updateStopUrl(newValue: String) {
        repository.stopUrl = newValue
        _uiState.update { it.copy(stopUrl = newValue) }
    }

    fun updateSampleUrl(newValue: String) {
        repository.sampleUrl = newValue
        _uiState.update { it.copy(sampleUrl = newValue) }
    }

    fun updateSamplesUrl(newValue: String) {
        repository.samplesUrl = newValue
        _uiState.update { it.copy(samplesUrl = newValue) }
    }

    fun updateBleDevice(address: String, name: String) {
        repository.bleDeviceAddress = address
        repository.bleDeviceName = name
        _uiState.update {
            it.copy(
                bleDeviceAddress = address,
                bleDeviceName = name,
            )
        }
    }

    fun updateObdProtocol(protocolName: String) {
        repository.obdProtocol = protocolName
        _uiState.update { it.copy(obdProtocol = protocolName) }
    }

    fun updateFuelType(typeName: String) {
        val preset = FuelTypePreset.fromName(typeName)
        repository.fuelType = preset.name
        if (preset.stoichAfr != null && preset.densityGl != null) {
            repository.fuelStoichAfr = preset.stoichAfr
            repository.fuelDensityGl = preset.densityGl
        }
        _uiState.update {
            it.copy(
                fuelType = preset.name,
                fuelStoichAfr = repository.fuelStoichAfr,
                fuelDensityGl = repository.fuelDensityGl,
            )
        }
    }

    fun updateFuelStoichAfr(value: Double) {
        repository.fuelStoichAfr = value
        repository.fuelType = FuelTypePreset.CUSTOM.name
        _uiState.update {
            it.copy(
                fuelStoichAfr = value,
                fuelType = FuelTypePreset.CUSTOM.name,
            )
        }
    }

    fun updateFuelDensityGl(value: Double) {
        repository.fuelDensityGl = value
        repository.fuelType = FuelTypePreset.CUSTOM.name
        _uiState.update {
            it.copy(
                fuelDensityGl = value,
                fuelType = FuelTypePreset.CUSTOM.name,
            )
        }
    }

    fun updateEngineDisplacementL(value: Double) {
        repository.engineDisplacementL = value
        _uiState.update { it.copy(engineDisplacementL = value) }
    }

    fun updateEngineVe(value: Double) {
        val clamped = value.coerceIn(0.0, 1.5)
        repository.engineVe = clamped
        _uiState.update { it.copy(engineVe = clamped) }
    }

    fun clearQrError() {
        _uiState.update { it.copy(qrError = "") }
    }

    fun clearConnectionTestMessage() {
        _uiState.update {
            it.copy(
                connectionTestMessage = "",
                connectionTestIsError = false,
            )
        }
    }

    fun testConnection() {
        val tester = connectionTester
        if (tester == null) {
            _uiState.update {
                it.copy(
                    connectionTestInProgress = false,
                    connectionTestMessage = "Connection test is not available on this platform",
                    connectionTestIsError = true,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionTestInProgress = true,
                    connectionTestMessage = "Testing…",
                    connectionTestIsError = false,
                )
            }
            val outcome = runCatching { tester.test() }.getOrElse {
                ConnectionTestOutcome.Failed(it.message ?: "test failed")
            }
            val (message, isError) = when (outcome) {
                ConnectionTestOutcome.Ok ->
                    "Connected — device token OK" to false
                is ConnectionTestOutcome.Unreachable -> {
                    val suffix = outcome.detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    "Can't reach server$suffix" to true
                }
                is ConnectionTestOutcome.Unauthorized ->
                    "Server reachable, token rejected" to true
                is ConnectionTestOutcome.Failed ->
                    outcome.detail.ifBlank { "Connection test failed" } to true
            }
            _uiState.update {
                it.copy(
                    connectionTestInProgress = false,
                    connectionTestMessage = message,
                    connectionTestIsError = isError,
                )
            }
        }
    }

    fun updateSettingsFromQr(qrContent: String) {
        try {
            val newState = json.decodeFromString(SettingsUiState.serializer(), qrContent)
            if (newState.apiToken.isNotEmpty()) repository.apiToken = newState.apiToken
            if (newState.startUrl.isNotEmpty()) repository.startUrl = newState.startUrl
            if (newState.stopUrl.isNotEmpty()) repository.stopUrl = newState.stopUrl
            if (newState.sampleUrl.isNotEmpty()) repository.sampleUrl = newState.sampleUrl
            if (newState.samplesUrl.isNotEmpty()) repository.samplesUrl = newState.samplesUrl
            if (newState.carId.isNotEmpty()) repository.carId = newState.carId
            if (newState.carName.isNotEmpty()) repository.carName = newState.carName
            if (newState.bleDeviceAddress.isNotEmpty()) repository.bleDeviceAddress = newState.bleDeviceAddress
            if (newState.bleDeviceName.isNotEmpty()) repository.bleDeviceName = newState.bleDeviceName
            if (newState.obdProtocol.isNotEmpty()) repository.obdProtocol = newState.obdProtocol
            if (newState.fuelType.isNotEmpty()) repository.fuelType = newState.fuelType
            repository.fuelStoichAfr = newState.fuelStoichAfr
            repository.fuelDensityGl = newState.fuelDensityGl
            repository.engineDisplacementL = newState.engineDisplacementL
            repository.engineVe = newState.engineVe

            _uiState.update {
                it.copy(
                    qrError = "",
                    connectionTestMessage = "",
                    connectionTestIsError = false,
                )
            }
            loadSettings()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(qrError = "Couldn't read QR settings: ${e.message ?: "invalid payload"}")
            }
        }
    }
}
