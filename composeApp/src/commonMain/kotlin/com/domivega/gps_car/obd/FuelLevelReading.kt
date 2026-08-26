package com.domivega.gps_car.obd

/**
 * Resolves fuel tank level (%) from polled values — SAE Mode 01 PID 0x2F.
 *
 * The VW cluster UDS candidate (DID 0x2206) is gone: it never produced a working
 * reading on the test vehicle, it is not corroborated by any public source, and
 * every possible payload byte decodes to a plausible-looking percentage, so a wrong
 * DID could not be caught by range-checking. Reaching it also meant switching the
 * ELM away from engine addressing, which breaks Mode 01 — far too high a price for
 * a value the standard PID already carries.
 */
object FuelLevelReading {
    fun fromPidValues(pidValues: Map<String, Double>): Double? =
        sourceKey(pidValues)?.let { pidValues[it] }

    /**
     * Which key [fromPidValues] resolved from, so callers can look up that PID's
     * freshness. Null when no source is present.
     */
    fun sourceKey(pidValues: Map<String, Double>): String? = when {
        pidValues.containsKey("2f") -> "2f"
        pidValues.containsKey("2F") -> "2F"
        else -> null
    }
}
