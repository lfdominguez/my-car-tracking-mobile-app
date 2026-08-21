package com.domivega.gps_car.obd

/**
 * Settings applied when the user selects [VehicleObdProfile.VwGolfMk4Tdi].
 * Capacity only — this ECU has no SAE tank/odometer PIDs.
 */
object GolfMk4TdiDefaults {
    const val PROTOCOL = "ISO_9141_2"
    const val DISPLACEMENT_L = 1.9
    const val TANK_CAPACITY_L = 55.0
}
