package com.domivega.gps_car.ui.state

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
    /** [ObdProtocol] enum name on Android; default CAN 11/500. */
    val obdProtocol: String = "ISO_15765_4_CAN_11_500",
    /** [VehicleObdProfile] enum name; default Generic. */
    val vehicleObdProfile: String = "Generic",
    /** Optional 4-hex DID override for VW MQB odometer (empty = candidates). */
    val vwOdometerDid: String = "",
    /** Experimental WWH-OBD / OBDonUDS engine stack only. */
    val wwhObdOnly: Boolean = false,
    /** FuelTypePreset name; default E10. */
    val fuelType: String = "E10",
    val fuelStoichAfr: Double = 14.08,
    val fuelDensityGl: Double = 745.0,
    val engineDisplacementL: Double = 1.0,
    val engineVe: Double = 0.85,
    /** Last QR parse error (cleared on successful apply). */
    val qrError: String = "",
    val connectionTestInProgress: Boolean = false,
    /** User-facing result of Test connection. */
    val connectionTestMessage: String = "",
    val connectionTestIsError: Boolean = false,
)
