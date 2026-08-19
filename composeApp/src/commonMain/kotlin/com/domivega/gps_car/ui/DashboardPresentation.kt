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

    fun clusterExtras(oilTempC: Double?, doorsSummary: String?): String? {
        val parts = buildList {
            if (oilTempC != null && oilTempC.isFinite()) add("Oil ${oilTempC.toInt()}°C")
            if (doorsSummary != null) add(doorsSummary)
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
