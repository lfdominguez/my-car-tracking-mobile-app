package com.domivega.gps_car.data

import kotlinx.coroutines.flow.StateFlow

interface CarMetricSource {
    val pidValues: StateFlow<Map<String, Double>>
    val ecuConnected: StateFlow<Boolean>
    val serviceVersion: StateFlow<String?>
    val pidNames: Map<String, String>
}
