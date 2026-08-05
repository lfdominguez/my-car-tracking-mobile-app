package com.domivega.gps_car.obd

/**
 * Resolves vehicle odometer (km) from polled OBD PID values.
 *
 * Prefer vendor UDS odometer (`ffodokm`) when present, then SAE J1979 PID 0xA6.
 * PID 0x31 is only "distance traveled since codes cleared" and must not be
 * treated as dash km.
 */
object OdometerReading {
    const val UDS_KM_KEY = "ffodokm"

    fun fromPidValues(pidValues: Map<String, Double>): Double? {
        pidValues[UDS_KM_KEY]?.let { return it }
        pidValues["a6"]?.let { return it }
        pidValues["A6"]?.let { return it }
        return null
    }
}
