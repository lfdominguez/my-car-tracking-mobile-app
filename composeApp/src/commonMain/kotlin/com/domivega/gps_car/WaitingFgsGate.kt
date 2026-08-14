package com.domivega.gps_car

/**
 * When the permanent WAITING foreground service (and notification) should be ensured.
 *
 * Product rule: only while a dongle address is configured — never for empty install.
 */
object WaitingFgsGate {
    fun shouldEnsureWaiting(bleDeviceAddress: String): Boolean =
        bleDeviceAddress.trim().isNotEmpty()
}
