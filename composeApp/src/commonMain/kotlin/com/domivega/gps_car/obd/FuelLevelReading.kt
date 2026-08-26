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

    /**
     * Which key [fromPidValues] resolved from, so callers can look up that PID's
     * freshness. Null when no source is present.
     */
    fun sourceKey(pidValues: Map<String, Double>): String? = when {
        pidValues.containsKey(VwClusterDids.KEY_FUEL_PCT) -> VwClusterDids.KEY_FUEL_PCT
        pidValues.containsKey("2f") -> "2f"
        pidValues.containsKey("2F") -> "2F"
        else -> null
    }
}
