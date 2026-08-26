package com.domivega.gps_car.data

import kotlinx.coroutines.flow.StateFlow

interface CarMetricSource {
    /** Staleness-filtered readings — what telemetry uploads. */
    val pidValues: StateFlow<Map<String, Double>>

    /**
     * Last successful reading per PID with no time expiry. The dashboard reads this
     * so a momentary dropout dims a value instead of slamming the gauge to 0.
     */
    val pidLastGood: StateFlow<Map<String, Double>>

    /** Keys of [pidLastGood] that are older than their tier's UI freshness budget. */
    val pidStale: StateFlow<Set<String>>

    val ecuConnected: StateFlow<Boolean>
    val serviceVersion: StateFlow<String?>
    val pidNames: Map<String, String>
}
