package com.domivega.gps_car.fuel

/** HV battery energy from SoC and pack capacity. */
object BatteryEnergyCalculator {
    /**
     * kWh used when SoC drops. Null if charging (SoC rose) or inputs unusable.
     */
    fun energyKwhFromSoc(
        startPct: Double?,
        endPct: Double?,
        capacityKwh: Double?,
    ): Double? {
        val start = startPct?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val end = endPct?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val cap = capacityKwh?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val drop = start - end
        if (drop <= 0.0) return null
        return drop / 100.0 * cap
    }

    /**
     * Instantaneous pack power (kW). Positive = discharge.
     * Current in A, voltage in V (HV pack if available).
     */
    fun powerKw(currentA: Double?, voltageV: Double?): Double? {
        val i = currentA?.takeIf { it.isFinite() } ?: return null
        val v = voltageV?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val kw = i * v / 1000.0
        return kw.takeIf { it.isFinite() }
    }
}
