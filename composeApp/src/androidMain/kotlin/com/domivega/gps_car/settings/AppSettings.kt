package com.domivega.gps_car.settings

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_TOKEN = "track_api_token"
        private const val KEY_START_URL = "track_start_url"
        private const val KEY_STOP_URL = "track_stop_url"
        private const val KEY_SAMPLE_URL = "track_sample_url"
        private const val KEY_SAMPLES_URL = "track_samples_url"
        private const val KEY_BLE_DEVICE_ADDRESS = "ble_device_address"
        private const val KEY_BLE_DEVICE_NAME = "ble_device_name"
        private const val KEY_BLUETOOTH_TRANSPORT = "bluetooth_transport"
        private const val KEY_OBD_PROTOCOL = "obd_protocol"
        private const val KEY_VEHICLE_OBD_PROFILE = "vehicle_obd_profile"
        private const val KEY_VW_ODOMETER_DID = "vw_odometer_did"
        private const val KEY_WWH_OBD_ONLY = "wwh_obd_only"
        private const val KEY_FUEL_TYPE = "fuel_type"
        private const val KEY_FUEL_STOICH_AFR = "fuel_stoich_afr"
        private const val KEY_FUEL_DENSITY_GL = "fuel_density_gl"
        private const val KEY_ENGINE_DISPLACEMENT_L = "engine_displacement_l"
        private const val KEY_ENGINE_VE = "engine_ve"
        private const val KEY_TANK_CAPACITY_L = "tank_capacity_l"
        private const val KEY_CAR_ID = "car_id"
        private const val KEY_CAR_NAME = "car_name"

        // Empty/placeholder defaults — configure real values in Settings (do not commit secrets).
        const val DEFAULT_API_TOKEN = ""
        const val DEFAULT_START_URL = "https://YOUR_SERVER.example/api/track/start"
        const val DEFAULT_STOP_URL = "https://YOUR_SERVER.example/api/track/stop"
        const val DEFAULT_SAMPLE_URL = "https://YOUR_SERVER.example/api/track/sample"
        const val DEFAULT_SAMPLES_URL = "https://YOUR_SERVER.example/api/track/samples"
        const val DEFAULT_BLUETOOTH_TRANSPORT = "Ble"
        const val DEFAULT_OBD_PROTOCOL = "ISO_15765_4_CAN_11_500"
        const val DEFAULT_VEHICLE_OBD_PROFILE = "Generic"
        const val DEFAULT_VW_ODOMETER_DID = ""
        const val DEFAULT_WWH_OBD_ONLY = false

        // Example vehicle defaults: compact 1.0L turbo on E10 (edit in Settings)
        const val DEFAULT_FUEL_TYPE = "E10"
        const val DEFAULT_FUEL_STOICH_AFR = 14.08
        const val DEFAULT_FUEL_DENSITY_GL = 745.0
        const val DEFAULT_ENGINE_DISPLACEMENT_L = 1.0
        const val DEFAULT_ENGINE_VE = 0.85
        /** 0 = unknown (no level cross-check). */
        const val DEFAULT_TANK_CAPACITY_L = 0.0
    }

    var apiToken: String
        get() = prefs.getString(KEY_API_TOKEN, DEFAULT_API_TOKEN) ?: DEFAULT_API_TOKEN
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value).apply()

    var startUrl: String
        get() = prefs.getString(KEY_START_URL, DEFAULT_START_URL) ?: DEFAULT_START_URL
        set(value) = prefs.edit().putString(KEY_START_URL, value).apply()

    var stopUrl: String
        get() = prefs.getString(KEY_STOP_URL, DEFAULT_STOP_URL) ?: DEFAULT_STOP_URL
        set(value) = prefs.edit().putString(KEY_STOP_URL, value).apply()

    var sampleUrl: String
        get() = prefs.getString(KEY_SAMPLE_URL, DEFAULT_SAMPLE_URL) ?: DEFAULT_SAMPLE_URL
        set(value) = prefs.edit().putString(KEY_SAMPLE_URL, value).apply()

    var samplesUrl: String
        get() = prefs.getString(KEY_SAMPLES_URL, DEFAULT_SAMPLES_URL) ?: DEFAULT_SAMPLES_URL
        set(value) = prefs.edit().putString(KEY_SAMPLES_URL, value).apply()

    var bleDeviceAddress: String
        get() = prefs.getString(KEY_BLE_DEVICE_ADDRESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BLE_DEVICE_ADDRESS, value).apply()

    var bleDeviceName: String
        get() = prefs.getString(KEY_BLE_DEVICE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BLE_DEVICE_NAME, value).apply()

    /** Stored as [com.domivega.gps_car.obd.BluetoothTransport] enum name. */
    var bluetoothTransport: String
        get() = prefs.getString(KEY_BLUETOOTH_TRANSPORT, DEFAULT_BLUETOOTH_TRANSPORT)
            ?: DEFAULT_BLUETOOTH_TRANSPORT
        set(value) = prefs.edit().putString(KEY_BLUETOOTH_TRANSPORT, value).apply()

    /** Stored as [com.domivega.gps_car.obd.ObdProtocol] enum name. */
    var obdProtocol: String
        get() = prefs.getString(KEY_OBD_PROTOCOL, DEFAULT_OBD_PROTOCOL) ?: DEFAULT_OBD_PROTOCOL
        set(value) = prefs.edit().putString(KEY_OBD_PROTOCOL, value).apply()

    /** Stored as [com.domivega.gps_car.obd.VehicleObdProfile] enum name. */
    var vehicleObdProfile: String
        get() = prefs.getString(KEY_VEHICLE_OBD_PROFILE, DEFAULT_VEHICLE_OBD_PROFILE) ?: DEFAULT_VEHICLE_OBD_PROFILE
        set(value) = prefs.edit().putString(KEY_VEHICLE_OBD_PROFILE, value).apply()

    /** Optional 4-hex DID override for VW MQB cluster odometer (empty = candidate list). */
    var vwOdometerDid: String
        get() = prefs.getString(KEY_VW_ODOMETER_DID, DEFAULT_VW_ODOMETER_DID) ?: DEFAULT_VW_ODOMETER_DID
        set(value) = prefs.edit().putString(KEY_VW_ODOMETER_DID, value).apply()

    /** Experimental: engine metrics via WWH-OBD / OBDonUDS 22F4xx only (no classic Mode 01). */
    var wwhObdOnly: Boolean
        get() = prefs.getBoolean(KEY_WWH_OBD_ONLY, DEFAULT_WWH_OBD_ONLY)
        set(value) = prefs.edit().putBoolean(KEY_WWH_OBD_ONLY, value).apply()

    /** Stored as [com.domivega.gps_car.fuel.FuelTypePreset] enum name. */
    var fuelType: String
        get() = prefs.getString(KEY_FUEL_TYPE, DEFAULT_FUEL_TYPE) ?: DEFAULT_FUEL_TYPE
        set(value) = prefs.edit().putString(KEY_FUEL_TYPE, value).apply()

    var fuelStoichAfr: Double
        get() = getDouble(KEY_FUEL_STOICH_AFR, DEFAULT_FUEL_STOICH_AFR)
        set(value) = putDouble(KEY_FUEL_STOICH_AFR, value)

    var fuelDensityGl: Double
        get() = getDouble(KEY_FUEL_DENSITY_GL, DEFAULT_FUEL_DENSITY_GL)
        set(value) = putDouble(KEY_FUEL_DENSITY_GL, value)

    var engineDisplacementL: Double
        get() = getDouble(KEY_ENGINE_DISPLACEMENT_L, DEFAULT_ENGINE_DISPLACEMENT_L)
        set(value) = putDouble(KEY_ENGINE_DISPLACEMENT_L, value)

    var engineVe: Double
        get() = getDouble(KEY_ENGINE_VE, DEFAULT_ENGINE_VE)
        set(value) = putDouble(KEY_ENGINE_VE, value)

    /** Fuel tank capacity in liters; 0 = unknown. */
    var tankCapacityL: Double
        get() = getDouble(KEY_TANK_CAPACITY_L, DEFAULT_TANK_CAPACITY_L)
        set(value) = putDouble(KEY_TANK_CAPACITY_L, value)

    var carId: String
        get() = prefs.getString(KEY_CAR_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CAR_ID, value).apply()

    var carName: String
        get() = prefs.getString(KEY_CAR_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CAR_NAME, value).apply()

    private fun getDouble(key: String, default: Double): Double =
        prefs.getString(key, null)?.toDoubleOrNull() ?: default

    private fun putDouble(key: String, value: Double) {
        prefs.edit().putString(key, value.toString()).apply()
    }
}
