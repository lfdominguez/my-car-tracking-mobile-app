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

        private const val KEY_UPLOAD_FUEL_CONSUMPTION_RATE = "upload_fuel_consumption_rate"
        private const val KEY_UPLOAD_ENGINE_LOAD_PCT = "upload_engine_load_pct"
        private const val KEY_UPLOAD_ABSOLUTE_ENGINE_LOAD_PCT = "upload_absolute_engine_load_pct"
        private const val KEY_UPLOAD_SHORT_TERM_FUEL_TRIM_PCT = "upload_short_term_fuel_trim_pct"
        private const val KEY_UPLOAD_LONG_TERM_FUEL_TRIM_PCT = "upload_long_term_fuel_trim_pct"
        private const val KEY_UPLOAD_FUEL_LEVEL_PCT = "upload_fuel_level_pct"
        private const val KEY_UPLOAD_ACCELERATOR_PEDAL_PCT = "upload_accelerator_pedal_pct"
        private const val KEY_UPLOAD_AMBIENT_AIR_TEMP_C = "upload_ambient_air_temp_c"
        private const val KEY_UPLOAD_ODOMETER_VALUE_KM = "upload_odometer_value_km"
        private const val KEY_UPLOAD_ENGINE_COOLANT_TEMP_C = "upload_engine_coolant_temp_c"
        private const val KEY_UPLOAD_MANIFOLD_ABSOLUTE_PRESSURE_KPA = "upload_manifold_absolute_pressure_kpa"
        private const val KEY_UPLOAD_CONTROL_MODULE_VOLTAGE = "upload_control_module_voltage"
        private const val KEY_UPLOAD_ENGINE_ON_TIME = "upload_engine_on_time"
        private const val KEY_UPLOAD_MASS_AIR_FLOW = "upload_mass_air_flow"
        private const val KEY_UPLOAD_LAMBDA_CMD = "upload_lambda_cmd"
        private const val KEY_UPLOAD_ATMOSPHERIC_PRESSURE = "upload_atmospheric_pressure"
        private const val KEY_UPLOAD_INTAKE_AIR_TEMPERATURE = "upload_intake_air_temperature"

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

    var uploadFuelConsumptionRate: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_FUEL_CONSUMPTION_RATE, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_FUEL_CONSUMPTION_RATE, value).apply()

    var uploadEngineLoadPct: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ENGINE_LOAD_PCT, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ENGINE_LOAD_PCT, value).apply()

    var uploadAbsoluteEngineLoadPct: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ABSOLUTE_ENGINE_LOAD_PCT, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ABSOLUTE_ENGINE_LOAD_PCT, value).apply()

    var uploadShortTermFuelTrimPct: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_SHORT_TERM_FUEL_TRIM_PCT, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_SHORT_TERM_FUEL_TRIM_PCT, value).apply()

    var uploadLongTermFuelTrimPct: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_LONG_TERM_FUEL_TRIM_PCT, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_LONG_TERM_FUEL_TRIM_PCT, value).apply()

    var uploadFuelLevelPct: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_FUEL_LEVEL_PCT, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_FUEL_LEVEL_PCT, value).apply()

    var uploadAcceleratorPedalPct: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ACCELERATOR_PEDAL_PCT, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ACCELERATOR_PEDAL_PCT, value).apply()

    var uploadAmbientAirTempC: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_AMBIENT_AIR_TEMP_C, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_AMBIENT_AIR_TEMP_C, value).apply()

    var uploadOdometerValueKm: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ODOMETER_VALUE_KM, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ODOMETER_VALUE_KM, value).apply()

    var uploadEngineCoolantTempC: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ENGINE_COOLANT_TEMP_C, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ENGINE_COOLANT_TEMP_C, value).apply()

    var uploadManifoldAbsolutePressureKpa: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_MANIFOLD_ABSOLUTE_PRESSURE_KPA, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_MANIFOLD_ABSOLUTE_PRESSURE_KPA, value).apply()

    var uploadControlModuleVoltage: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_CONTROL_MODULE_VOLTAGE, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_CONTROL_MODULE_VOLTAGE, value).apply()

    var uploadEngineOnTime: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ENGINE_ON_TIME, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ENGINE_ON_TIME, value).apply()

    var uploadMassAirFlow: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_MASS_AIR_FLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_MASS_AIR_FLOW, value).apply()

    var uploadLambdaCmd: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_LAMBDA_CMD, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_LAMBDA_CMD, value).apply()

    var uploadAtmosphericPressure: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_ATMOSPHERIC_PRESSURE, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_ATMOSPHERIC_PRESSURE, value).apply()

    var uploadIntakeAirTemperature: Boolean
        get() = prefs.getBoolean(KEY_UPLOAD_INTAKE_AIR_TEMPERATURE, true)
        set(value) = prefs.edit().putBoolean(KEY_UPLOAD_INTAKE_AIR_TEMPERATURE, value).apply()

    fun sampleUploadFieldFlags(): SampleUploadFieldFlags = SampleUploadFieldFlags(
        fuelConsumptionRate = uploadFuelConsumptionRate,
        engineLoadPct = uploadEngineLoadPct,
        absoluteEngineLoadPct = uploadAbsoluteEngineLoadPct,
        shortTermFuelTrimPct = uploadShortTermFuelTrimPct,
        longTermFuelTrimPct = uploadLongTermFuelTrimPct,
        fuelLevelPct = uploadFuelLevelPct,
        acceleratorPedalPct = uploadAcceleratorPedalPct,
        ambientAirTempC = uploadAmbientAirTempC,
        odometerValueKm = uploadOdometerValueKm,
        engineCoolantTempC = uploadEngineCoolantTempC,
        manifoldAbsolutePressureKpa = uploadManifoldAbsolutePressureKpa,
        controlModuleVoltage = uploadControlModuleVoltage,
        engineOnTime = uploadEngineOnTime,
        massAirFlow = uploadMassAirFlow,
        lambdaCmd = uploadLambdaCmd,
        atmosphericPressure = uploadAtmosphericPressure,
        intakeAirTemperature = uploadIntakeAirTemperature,
    )

    fun applySampleUploadFieldFlags(flags: SampleUploadFieldFlags) {
        uploadFuelConsumptionRate = flags.fuelConsumptionRate
        uploadEngineLoadPct = flags.engineLoadPct
        uploadAbsoluteEngineLoadPct = flags.absoluteEngineLoadPct
        uploadShortTermFuelTrimPct = flags.shortTermFuelTrimPct
        uploadLongTermFuelTrimPct = flags.longTermFuelTrimPct
        uploadFuelLevelPct = flags.fuelLevelPct
        uploadAcceleratorPedalPct = flags.acceleratorPedalPct
        uploadAmbientAirTempC = flags.ambientAirTempC
        uploadOdometerValueKm = flags.odometerValueKm
        uploadEngineCoolantTempC = flags.engineCoolantTempC
        uploadManifoldAbsolutePressureKpa = flags.manifoldAbsolutePressureKpa
        uploadControlModuleVoltage = flags.controlModuleVoltage
        uploadEngineOnTime = flags.engineOnTime
        uploadMassAirFlow = flags.massAirFlow
        uploadLambdaCmd = flags.lambdaCmd
        uploadAtmosphericPressure = flags.atmosphericPressure
        uploadIntakeAirTemperature = flags.intakeAirTemperature
    }

    private fun getDouble(key: String, default: Double): Double =
        prefs.getString(key, null)?.toDoubleOrNull() ?: default

    private fun putDouble(key: String, value: Double) {
        prefs.edit().putString(key, value.toString()).apply()
    }
}
