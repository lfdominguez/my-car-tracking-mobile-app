package com.domivega.gps_car.ui.state

data class DashboardState(
    val rpm: Double = 0.0,
    val speed: Double = 0.0,
    val engineLoad: Double = 0.0,
    val fuelLevel: Double = 0.0,
    /** Vehicle odometer km from cluster UDS or SAE PID A6 when available. */
    val odometerKm: Double? = null,
    /** Oil temp °C from VW cluster UDS when available. */
    val oilTempC: Double? = null,
    /** Human door summary from VW cluster UDS, or null if never read. */
    val doorsSummary: String? = null,
    val isTracking: Boolean = false,
    val obdEnabled: Boolean = true,
    val isGpsLocked: Boolean = false,
    val ecuConnected: Boolean = false,
    /** Non-null when sample upload has FAILED/DEAD rows or last flush failed. */
    val uploadWarning: String? = null,
    val serviceVersion: String? = null,
    val pidValues: Map<String, Double> = emptyMap(),
    val pidNames: Map<String, String> = emptyMap()
)
