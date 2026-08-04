package com.domivega.gps_car.obd

enum class ObdLogLevel {
    INFO,
    WARN,
    ERROR,
}

data class ObdLogEntry(
    val timestampMs: Long,
    val level: ObdLogLevel,
    val message: String,
)
