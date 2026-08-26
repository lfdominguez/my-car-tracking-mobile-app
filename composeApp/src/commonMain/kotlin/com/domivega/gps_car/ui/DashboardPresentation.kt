package com.domivega.gps_car.ui

object DashboardPresentation {
    fun trackingLabel(isTracking: Boolean): String =
        if (isTracking) "ACTIVE TRACKING" else "IDLE"

    fun odometerLabel(odometerKm: Double?): String {
        if (odometerKm == null || !odometerKm.isFinite()) return "— km"
        return if (odometerKm >= 100.0) {
            "${odometerKm.toLong()} km"
        } else {
            val tenths = ((odometerKm * 10.0) + 0.5).toInt()
            "${tenths / 10}.${tenths % 10} km"
        }
    }

    /**
     * Hybrid/EV pack line from SAE PID 0x9A, e.g. `-18.4 kW · 355 V · -51.8 A`.
     *
     * Replaces the old VW cluster oil/doors line: those DIDs were never confirmed
     * working and cost a header switch that breaks Mode 01, so they were removed.
     * Negative power is charge going into the pack (regen or plug-in charging).
     */
    fun hvBatterySummary(
        packKw: Double?,
        packVolts: Double?,
        packAmps: Double?,
    ): String? {
        val parts = buildList {
            if (packKw != null && packKw.isFinite()) add("${oneDecimal(packKw)} kW")
            if (packVolts != null && packVolts.isFinite()) add("${packVolts.toInt()} V")
            if (packAmps != null && packAmps.isFinite()) add("${oneDecimal(packAmps)} A")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** One-decimal rendering without String.format, which common Kotlin lacks. */
    private fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10.0).toLong()
        val whole = tenths / 10
        val frac = kotlin.math.abs(tenths % 10)
        val sign = if (tenths < 0 && whole == 0L) "-" else ""
        return "$sign$whole.$frac"
    }
}
