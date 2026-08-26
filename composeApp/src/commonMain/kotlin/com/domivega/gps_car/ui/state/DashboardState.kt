package com.domivega.gps_car.ui.state

/**
 * One dashboard metric. [isStale] means the reading is real but older than its
 * tier's freshness budget — the tile dims it rather than blanking it, so a
 * momentary OBD dropout never moves the number. `null` in [DashboardState] means
 * the PID has not decoded at all this session and renders as an em dash.
 */
data class Reading(
    val value: Double,
    val isStale: Boolean = false,
)

data class DashboardState(
    /** Null until the PID decodes; never coalesced to 0.0 — see [Reading]. */
    val rpm: Reading? = null,
    val speed: Reading? = null,
    val engineLoad: Reading? = null,
    val fuelLevel: Reading? = null,
    /** Vehicle odometer km from cluster UDS or SAE PID A6 when available. */
    val odometerKm: Double? = null,
    /** Hybrid/EV pack power (kW), negative while charging. From SAE PID 0x9A. */
    val hvPackKw: Double? = null,
    /** Hybrid/EV pack voltage (V). From SAE PID 0x9A. */
    val hvPackVolts: Double? = null,
    /** Hybrid/EV pack current (A), negative while charging. From SAE PID 0x9A. */
    val hvPackAmps: Double? = null,
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
