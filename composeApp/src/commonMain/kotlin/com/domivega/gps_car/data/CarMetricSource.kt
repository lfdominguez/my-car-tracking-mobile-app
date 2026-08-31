package com.domivega.gps_car.data

import kotlinx.coroutines.flow.StateFlow

interface CarMetricSource {
    /**
     * Staleness-filtered readings — what telemetry uploads, and what the dashboard
     * gauges show. A momentary dropout still dims rather than slamming a gauge to 0,
     * because the expiry budgets (10 s HOT / 30 s SLOW) are far longer than the UI
     * dim thresholds; past those budgets the reading is gone here too, so the gauge
     * and the uploaded sample can no longer disagree.
     */
    val pidValues: StateFlow<Map<String, Double>>

    /**
     * Last successful reading per PID with no time expiry. Only the Debug Console's
     * raw PID list reads this — it is a "last seen" view by design. Never drive a
     * gauge from it: nothing ages these out, so a dead PID would keep displaying its
     * final value for the rest of the session.
     */
    val pidLastGood: StateFlow<Map<String, Double>>

    /** Keys that are older than their tier's UI freshness budget. */
    val pidStale: StateFlow<Set<String>>

    val ecuConnected: StateFlow<Boolean>
    val serviceVersion: StateFlow<String?>
    val pidNames: Map<String, String>
}
