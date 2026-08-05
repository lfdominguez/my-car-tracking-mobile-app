package com.domivega.gps_car.obd

/**
 * Resolves fuel tank level (%) from polled values.
 * Prefer VW cluster UDS (`ffuelpct`) when present, else SAE Mode 01 PID 0x2F.
 */
object FuelLevelReading {
    fun fromPidValues(pidValues: Map<String, Double>): Double? {
        pidValues[VwClusterDids.KEY_FUEL_PCT]?.let { return it }
        pidValues["2f"]?.let { return it }
        pidValues["2F"]?.let { return it }
        return null
    }
}
