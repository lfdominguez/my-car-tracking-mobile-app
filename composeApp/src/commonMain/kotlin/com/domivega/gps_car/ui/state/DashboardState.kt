package com.domivega.gps_car.ui.state

data class DashboardState(
    val rpm: Double = 0.0,
    val speed: Double = 0.0,
    val engineLoad: Double = 0.0,
    val fuelLevel: Double = 0.0,
    /** Vehicle odometer km from SAE PID A6 when available. */
    val odometerKm: Double? = null,
    val isTracking: Boolean = false,
    val isGpsLocked: Boolean = false,
    val ecuConnected: Boolean = false,
    val serviceVersion: String? = null,
    val pidValues: Map<String, Double> = emptyMap(),
    val pidNames: Map<String, String> = emptyMap()
)
