package com.domivega.gps_car.fuel

data class FuelCalcConfig(
    val stoichAfr: Double,
    val densityGl: Double,
    val displacementL: Double,
    val ve: Double,
)

data class FuelCalcSensors(
    val mafGs: Double? = null,
    val lambda: Double? = null,
    val mapKpa: Double? = null,
    val rpm: Double? = null,
    val iatC: Double? = null,
    val stftPct: Double? = null,
    val ltftPct: Double? = null,
    /** SAE PID 5E L/h when present — wins over air-path estimate. */
    val ecuFuelRateLh: Double? = null,
)

object FuelConsumptionCalculator {
    private const val AIR_MOLAR_MASS = 28.97 // g/mol
    private const val R_UNIV = 8.314 // J/(mol·K)
    private const val FOUR_STROKE_REV_FACTOR = 120.0

    fun litersPerHour(config: FuelCalcConfig, sensors: FuelCalcSensors): Double? {
        sensors.ecuFuelRateLh?.let { r ->
            if (r.isFinite() && r > 0.0 && r <= 100.0) return r
        }
        if (config.stoichAfr <= 0.0 || config.densityGl <= 0.0) return null
        val airGs = airMassGs(config, sensors) ?: return null
        if (airGs <= 0.0 || airGs.isNaN() || airGs.isInfinite()) return null
        val lambda = sensors.lambda?.takeIf { it in 0.5..1.5 } ?: 1.0
        val trim = trimFactor(sensors.stftPct, sensors.ltftPct)
        val lh = airGs * 3600.0 / (config.stoichAfr * lambda * config.densityGl) * trim
        return lh.takeIf { it.isFinite() && it > 0.0 && it <= 200.0 }
    }

    /**
     * Multiplicative correction from STFT+LTFT (%). Each trim used only if in ±25%.
     * Clamped to 0.7…1.3. Applied only on estimated (non-5E) path.
     */
    fun trimFactor(stftPct: Double?, ltftPct: Double?): Double {
        fun one(t: Double?) = t?.takeIf { it in -25.0..25.0 } ?: 0.0
        return (1.0 + (one(stftPct) + one(ltftPct)) / 100.0).coerceIn(0.7, 1.3)
    }

    /**
     * Air mass flow g/s: prefer sensor MAF, else MAP×RPM×IAT speed-density estimate.
     * Used for fuel L/h and as fallback when Mode 01 PID 0x10 is unsupported.
     */
    fun airMassGs(config: FuelCalcConfig, sensors: FuelCalcSensors): Double? {
        val maf = sensors.mafGs
        if (maf != null && maf > 0.0 && maf.isFinite()) return maf

        val map = sensors.mapKpa
        val rpm = sensors.rpm
        val iatC = sensors.iatC
        if (map == null || rpm == null || iatC == null) return null
        if (map <= 0.0 || rpm <= 0.0 || config.displacementL <= 0.0 || config.ve <= 0.0) return null
        val iatK = iatC + 273.15
        if (iatK <= 0.0) return null
        return map * config.ve * config.displacementL * rpm * AIR_MOLAR_MASS /
            (R_UNIV * iatK * FOUR_STROKE_REV_FACTOR)
    }
}
