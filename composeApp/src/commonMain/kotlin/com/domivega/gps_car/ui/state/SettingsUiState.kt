package com.domivega.gps_car.ui.state

import com.domivega.gps_car.settings.SampleUploadFieldFlags
import kotlinx.serialization.Serializable

@Serializable
data class SettingsUiState(
    val apiToken: String = "",
    val startUrl: String = "",
    val stopUrl: String = "",
    val sampleUrl: String = "",
    val samplesUrl: String = "",
    /** Optional car identity from platform QR provisioning (not required for ingest). */
    val carId: String = "",
    val carName: String = "",
    val bleDeviceAddress: String = "",
    val bleDeviceName: String = "",
    /** [BluetoothTransport] enum name; default Ble. */
    val bluetoothTransport: String = "Ble",
    /** [ObdProtocol] enum name on Android; default CAN 11/500. */
    val obdProtocol: String = "ISO_15765_4_CAN_11_500",
    /** [VehicleObdProfile] enum name; default Generic. */
    val vehicleObdProfile: String = "Generic",
    /** Optional 4-hex DID override for VW MQB odometer (empty = candidates). */
    val vwOdometerDid: String = "",
    /** Experimental WWH-OBD / OBDonUDS engine stack only. */
    val wwhObdOnly: Boolean = false,
    /** Faster ELM polling (ATAT2 + Mode 01 line suffix). Default off. */
    val obdPerformanceMode: Boolean = false,
    val obdEnabled: Boolean = true,
    /** [FuelClass] enum name; default GASOLINE. */
    val fuelClass: String = "GASOLINE",
    /** FuelTypePreset name; default E10. */
    val fuelType: String = "E10",
    val fuelStoichAfr: Double = 14.08,
    val fuelDensityGl: Double = 745.0,
    val engineDisplacementL: Double = 1.0,
    val engineVe: Double = 0.85,
    /** Liters; 0 = unknown (no tank-level fuel cross-check). */
    val tankCapacityL: Double = 0.0,
    val batteryCapacityKwh: Double = 0.0,
    /** Optional Sample metrics to upload (lat/lon/speed/RPM always on). */
    val sampleUploadFieldFlags: SampleUploadFieldFlags = SampleUploadFieldFlags.ALL_ENABLED,
    /** Last QR parse error (cleared on successful apply). */
    val qrError: String = "",
    val connectionTestInProgress: Boolean = false,
    /** User-facing result of Test connection. */
    val connectionTestMessage: String = "",
    val connectionTestIsError: Boolean = false,
)
