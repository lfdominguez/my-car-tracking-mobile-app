package com.domivega.gps_car.data

import com.domivega.gps_car.obd.ObdBleManager
import kotlinx.coroutines.flow.StateFlow

class AndroidCarMetricSource : CarMetricSource {
    override val pidValues: StateFlow<Map<String, Double>> = ObdBleManager.pidValues
    override val pidLastGood: StateFlow<Map<String, Double>> = ObdBleManager.pidLastGood
    override val pidStale: StateFlow<Set<String>> = ObdBleManager.pidStale
    override val ecuConnected: StateFlow<Boolean> = ObdBleManager.ecuConnected

    // CarMetricSource expects StateFlow<String?>; connectionStatus is non-null String.
    @Suppress("UNCHECKED_CAST")
    override val serviceVersion: StateFlow<String?> =
        ObdBleManager.connectionStatus as StateFlow<String?>

    override val pidNames: Map<String, String> = ObdBleManager.pidsMap
}
