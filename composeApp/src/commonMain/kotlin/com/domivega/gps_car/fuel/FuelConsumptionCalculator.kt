package com.domivega.gps_car.fuel

data class FuelCalcConfig(
    val stoichAfr: Double,
    val densityGl: Double,
    val displacementL: Double,
    val ve: Double,
    val isDiesel: Boolean = false,
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
    /** OBD speed (kph); null treated as stopped for idle peak-air MAF detect. */
    val speedKph: Double? = null,
    /** SAE PID 04 calculated load (%); used for diesel AFR. */
    val calculatedLoadPct: Double? = null,
)

object FuelConsumptionCalculator {
    private const val AIR_MOLAR_MASS = 28.97 // g/mol
    private const val R_UNIV = 8.314 // J/(mol·K)
    private const val FOUR_STROKE_REV_FACTOR = 120.0

    /** Dry air density for NA peak-air estimate (kg/m³, ~25 °C). Matches server fuel_stats. */
    const val AIR_DENSITY_KG_M3 = 1.184

    /**
     * Low-MAP fraction of atmosphere used only when replacing peak-air idle fraud.
     * Max-plausible economy bound (min credible idle) so trip MPG can track the dash.
     */
    const val IDLE_MAP_FRACTION = 0.14

    /** Treat below this speed as stopped for idle-rate sanitizing. */
    const val IDLE_SPEED_MAX_KPH = 1.0
    const val IDLE_RPM_MIN = 400.0
    const val IDLE_RPM_MAX = 1500.0

    /** Typical TDI cruise AFR (lean of smoke limit, not gasoline stoich). */
    const val DIESEL_AFR_CRUISE = 17.5

    /** Typical TDI idle AFR when PID 04 load is ~0%. */
    const val DIESEL_AFR_IDLE = 38.0

    /** Load at/above this uses cruise AFR; below blends toward idle AFR. */
    const val DIESEL_IDLE_LOAD_MAX_PCT = 25.0

    /** MAF ≥ this × peak air at the same RPM is treated as “wide-open at idle RPM”. */
    const val PEAK_AIR_DETECT_RATIO = 0.70

    fun litersPerHour(config: FuelCalcConfig, sensors: FuelCalcSensors): Double? {
        sensors.ecuFuelRateLh?.let { r ->
            if (r.isFinite() && r > 0.0 && r <= 100.0) return r
        }
        if (config.densityGl <= 0.0) return null
        if (!config.isDiesel && config.stoichAfr <= 0.0) return null
        val airGs = airMassGs(config, sensors) ?: return null
        if (airGs <= 0.0 || airGs.isNaN() || airGs.isInfinite()) return null
        if (config.isDiesel) {
            val afr = dieselAfr(sensors.calculatedLoadPct)
            val dieselLh = airGs * 3600.0 / (afr * config.densityGl)
            return dieselLh.takeIf { it.isFinite() && it > 0.0 && it <= 200.0 }
        }
        val lambda = sensors.lambda?.takeIf { it in 0.5..1.5 } ?: 1.0
        val trim = trimFactor(sensors.stftPct, sensors.ltftPct)
        val lh = airGs * 3600.0 / (config.stoichAfr * lambda * config.densityGl) * trim
        return lh.takeIf { it.isFinite() && it > 0.0 && it <= 200.0 }
    }

    /**
     * Diesel AFR from PID 04 load: lean at idle (0% → 38), cruise from 25% up (17.5).
     * Unknown/invalid load uses cruise so highway is not understated.
     */
    fun dieselAfr(loadPct: Double?): Double {
        if (loadPct == null || !loadPct.isFinite()) return DIESEL_AFR_CRUISE
        val load = loadPct.coerceIn(0.0, 100.0)
        if (load >= DIESEL_IDLE_LOAD_MAX_PCT) return DIESEL_AFR_CRUISE
        val t = load / DIESEL_IDLE_LOAD_MAX_PCT
        return DIESEL_AFR_IDLE + (DIESEL_AFR_CRUISE - DIESEL_AFR_IDLE) * t
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
     * Peak naturally-aspirated air mass (g/s) at atmospheric pressure and 100% VE:
     * `V × (rpm/2/60) × ρ_air`.
     */
    fun peakAirMassGs(displacementL: Double, rpm: Double): Double? {
        if (!(displacementL > 0.0 && rpm > 0.0)) return null
        if (!displacementL.isFinite() || !rpm.isFinite()) return null
        return displacementL * rpm * AIR_DENSITY_KG_M3 / FOUR_STROKE_REV_FACTOR
    }

    /**
     * True when sensor MAF is present, finite, positive, and not peak-air idle fraud.
     * Used by OBD layer to decide whether to publish estimated MAF for samples/UI.
     */
    fun isTrustedSensorMaf(config: FuelCalcConfig, sensors: FuelCalcSensors): Boolean {
        val maf = sensors.mafGs ?: return false
        if (!(maf > 0.0 && maf.isFinite())) return false
        return !isPeakAirIdleMaf(maf, sensors.speedKph, sensors.rpm, config.displacementL)
    }

    /**
     * Cheap OBD paths sometimes emit MAF ≈ peak atmospheric air while stopped.
     * Detect only at idle RPM and near-zero speed.
     */
    fun isPeakAirIdleMaf(
        mafGs: Double,
        speedKph: Double?,
        rpm: Double?,
        displacementL: Double,
    ): Boolean {
        if (!(mafGs.isFinite() && mafGs > 0.0)) return false
        if (!(displacementL.isFinite() && displacementL > 0.0)) return false
        val speed = speedKph?.takeIf { it.isFinite() } ?: 0.0
        val r = rpm?.takeIf { it.isFinite() } ?: return false
        if (speed >= IDLE_SPEED_MAX_KPH || r !in IDLE_RPM_MIN..IDLE_RPM_MAX) return false
        val peak = peakAirMassGs(displacementL, r) ?: return false
        return mafGs >= peak * PEAK_AIR_DETECT_RATIO
    }

    /**
     * Air mass flow g/s: prefer trusted sensor MAF, else MAP×RPM×IAT speed-density,
     * else max-plausible idle bound when MAF was peak-air fraud at idle.
     */
    fun airMassGs(config: FuelCalcConfig, sensors: FuelCalcSensors): Double? {
        val maf = sensors.mafGs?.takeIf { it > 0.0 && it.isFinite() }
        if (maf != null &&
            !isPeakAirIdleMaf(maf, sensors.speedKph, sensors.rpm, config.displacementL)
        ) {
            return maf
        }

        mapAirMassGs(config, sensors)?.let { return it }

        // Rejected peak-air idle MAF and no MAP: use peak × VE × low-MAP fraction.
        if (maf != null &&
            isPeakAirIdleMaf(maf, sensors.speedKph, sensors.rpm, config.displacementL)
        ) {
            val rpm = sensors.rpm?.takeIf { it.isFinite() && it > 0.0 } ?: return null
            if (!(config.displacementL > 0.0 && config.ve > 0.0)) return null
            val peak = peakAirMassGs(config.displacementL, rpm) ?: return null
            val bound = peak * config.ve * IDLE_MAP_FRACTION
            return bound.takeIf { it.isFinite() && it > 0.0 }
        }
        return null
    }

    private fun mapAirMassGs(config: FuelCalcConfig, sensors: FuelCalcSensors): Double? {
        val map = sensors.mapKpa
        val rpm = sensors.rpm
        val iatC = sensors.iatC
        if (map == null || rpm == null || iatC == null) return null
        if (map <= 0.0 || rpm <= 0.0 || config.displacementL <= 0.0 || config.ve <= 0.0) return null
        val iatK = iatC + 273.15
        if (iatK <= 0.0) return null
        val air = map * config.ve * config.displacementL * rpm * AIR_MOLAR_MASS /
            (R_UNIV * iatK * FOUR_STROKE_REV_FACTOR)
        return air.takeIf { it.isFinite() && it > 0.0 }
    }
}
