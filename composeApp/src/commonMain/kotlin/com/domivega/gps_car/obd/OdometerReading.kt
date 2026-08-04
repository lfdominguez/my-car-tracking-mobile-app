package com.domivega.gps_car.obd

/**
 * Resolves vehicle odometer (km) from polled OBD PID values.
 *
 * Prefer SAE J1979 PID 0xA6 (true odometer). PID 0x31 is only
 * "distance traveled since codes cleared" and must not be treated as dash km.
 */
object OdometerReading {
    fun fromPidValues(pidValues: Map<String, Double>): Double? {
        pidValues["a6"]?.let { return it }
        pidValues["A6"]?.let { return it }
        return null
    }
}
