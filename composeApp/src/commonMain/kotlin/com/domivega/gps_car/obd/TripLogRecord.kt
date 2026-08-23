package com.domivega.gps_car.obd

data class TripLogRecord(
    val id: Long,
    val trackingId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val logText: String,
)
