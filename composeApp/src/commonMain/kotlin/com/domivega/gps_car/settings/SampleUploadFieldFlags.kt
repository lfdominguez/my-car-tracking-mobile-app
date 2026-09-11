package com.domivega.gps_car.settings

import kotlinx.serialization.Serializable

/**
 * Which optional sample metrics may be uploaded.
 * Always-on: tracking_id, recorded_at, lat, lon, acc, RPM, velocity.
 */
@Serializable
data class SampleUploadFieldFlags(
    val fuelConsumptionRate: Boolean = true,
    val engineLoadPct: Boolean = true,
    val absoluteEngineLoadPct: Boolean = true,
    val shortTermFuelTrimPct: Boolean = true,
    val longTermFuelTrimPct: Boolean = true,
    val fuelLevelPct: Boolean = true,
    val acceleratorPedalPct: Boolean = true,
    val ambientAirTempC: Boolean = true,
    val odometerValueKm: Boolean = true,
    val engineCoolantTempC: Boolean = true,
    val manifoldAbsolutePressureKpa: Boolean = true,
    val controlModuleVoltage: Boolean = true,
    val engineOnTime: Boolean = true,
    val massAirFlow: Boolean = true,
    val lambdaCmd: Boolean = true,
    val atmosphericPressure: Boolean = true,
    val intakeAirTemperature: Boolean = true,
    /**
     * Phone accelerometer aggregates. Grouped under one flag because the three fields
     * are useless apart, and offered at all because motion is phone data rather than
     * car data — a user may reasonably want the car telemetry without it.
     */
    val motion: Boolean = true,
) {
    companion object {
        val ALL_ENABLED = SampleUploadFieldFlags()
    }
}
