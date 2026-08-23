package com.domivega.gps_car.obd

/**
 * Hold-last-good filter for OBD speed/RPM spikes (cheap adapters, VW diesel hiccups).
 *
 * When phone GPS speed is available, implausible OBD speed (range/rate spike,
 * instantaneous 0 vs moving GPS, or large disagreement) is replaced with GPS
 * instead of the previous OBD value.
 */
class TelemetrySpikeFilter(
    private val maxSpeedKph: Double = 250.0,
    private val maxRpm: Double = 8000.0,
    private val maxSpeedDeltaPerS: Double = 35.0,
    private val maxRpmDeltaPerS: Double = 3500.0,
    private val gpsDisagreeKph: Double = 25.0,
) {
    private var lastSpeed: Double? = null
    private var lastSpeedAtMs: Long? = null
    private var lastRpm: Double? = null
    private var lastRpmAtMs: Long? = null

    data class Sample(
        val speedKph: Double?,
        val rpm: Double?,
    )

    fun accept(
        speedKph: Double?,
        rpm: Double?,
        nowMs: Long,
        gpsSpeedKph: Double? = null,
    ): Sample {
        val speed = acceptSpeed(speedKph, gpsSpeedKph, nowMs)
        val r = acceptOne(rpm, nowMs, lastRpm, lastRpmAtMs, maxRpm, maxRpmDeltaPerS)
        if (speed != null) {
            lastSpeed = speed
            lastSpeedAtMs = nowMs
        }
        if (r != null) {
            lastRpm = r
            lastRpmAtMs = nowMs
        }
        return Sample(speed, r)
    }

    fun reset() {
        lastSpeed = null
        lastSpeedAtMs = null
        lastRpm = null
        lastRpmAtMs = null
    }

    private fun acceptSpeed(obd: Double?, gps: Double?, nowMs: Long): Double? {
        val gpsValid = sanitize(gps, maxSpeedKph)
        val obdValid = sanitize(obd, maxSpeedKph)
        if (obdValid == null) {
            return gpsValid ?: lastSpeed
        }
        val rateBad = isRateSpike(obdValid, lastSpeed, lastSpeedAtMs, nowMs, maxSpeedDeltaPerS)
        val disagrees = gpsValid != null && kotlin.math.abs(obdValid - gpsValid) > gpsDisagreeKph
        return if (rateBad || disagrees) {
            gpsValid ?: lastSpeed
        } else {
            obdValid
        }
    }

    private fun acceptOne(
        next: Double?,
        nowMs: Long,
        prev: Double?,
        prevAtMs: Long?,
        maxAbs: Double,
        maxDeltaPerS: Double,
    ): Double? {
        val v = sanitize(next, maxAbs) ?: return prev
        if (isRateSpike(v, prev, prevAtMs, nowMs, maxDeltaPerS)) return prev
        return v
    }

    private fun sanitize(value: Double?, maxAbs: Double): Double? {
        val v = value ?: return null
        if (!v.isFinite() || v < 0.0 || v > maxAbs) return null
        return v
    }

    private fun isRateSpike(
        next: Double,
        prev: Double?,
        prevAtMs: Long?,
        nowMs: Long,
        maxDeltaPerS: Double,
    ): Boolean {
        if (prev == null || prevAtMs == null) return false
        val dtS = (nowMs - prevAtMs) / 1000.0
        if (dtS <= 0.0 || dtS > 8.0) return false
        return kotlin.math.abs(next - prev) / dtS > maxDeltaPerS
    }
}
