package com.domivega.gps_car.obd

enum class VehicleObdProfile {
    Generic,
    VwMqb;

    val displayName: String
        get() = when (this) {
            Generic -> "Generic OBD"
            VwMqb -> "VW MQB (Nivus)"
        }

    companion object {
        val DEFAULT = Generic

        fun fromName(name: String): VehicleObdProfile =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
