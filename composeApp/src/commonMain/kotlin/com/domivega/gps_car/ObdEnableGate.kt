package com.domivega.gps_car

/**
 * Policy for the persisted OBD Enable/Disable switch.
 * When disabled, no connect path may touch the adapter.
 */
object ObdEnableGate {
    fun mayConnect(obdEnabled: Boolean): Boolean = obdEnabled

    fun disableRequiresConfirmation(isTracking: Boolean): Boolean = isTracking
}
