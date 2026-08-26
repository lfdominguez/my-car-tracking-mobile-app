package com.domivega.gps_car.obd

/**
 * Resolves fuel tank level (%) from polled values.
 *
 * Prefers the VW cluster UDS reading (`ffuelpct`) over SAE Mode 01 PID 0x2F,
 * because on VW it is the value the dash actually shows — but only while the two
 * agree. DID 0x2206 is not corroborated by any public source, and every possible
 * payload byte decodes to a plausible-looking percentage, so a wrong DID cannot
 * be caught by range-checking alone. Disagreement past [AGREEMENT_TOLERANCE_PCT]
 * is the one signal available that the cluster candidate is not fuel level, and
 * the standard PID wins that tie.
 */
object FuelLevelReading {
    /** Percentage points of cluster-vs-PID-0x2F disagreement tolerated. */
    const val AGREEMENT_TOLERANCE_PCT: Double = 15.0

    fun fromPidValues(pidValues: Map<String, Double>): Double? =
        sourceKey(pidValues)?.let { pidValues[it] }

    /**
     * Which key [fromPidValues] resolved from, so callers can look up that PID's
     * freshness. Null when no source is present.
     */
    fun sourceKey(pidValues: Map<String, Double>): String? {
        val saeKey = when {
            pidValues.containsKey("2f") -> "2f"
            pidValues.containsKey("2F") -> "2F"
            else -> null
        }
        val cluster = pidValues[VwClusterDids.KEY_FUEL_PCT]
            ?: return saeKey
        val sae = saeKey?.let { pidValues[it] }
            ?: return VwClusterDids.KEY_FUEL_PCT

        val agrees = cluster.isFinite() &&
            sae.isFinite() &&
            kotlin.math.abs(cluster - sae) <= AGREEMENT_TOLERANCE_PCT
        return if (agrees) VwClusterDids.KEY_FUEL_PCT else saeKey
    }
}
