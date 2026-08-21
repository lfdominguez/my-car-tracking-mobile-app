package com.domivega.gps_car.obd

enum class VehicleObdProfile {
    Generic,
    VwMqb,
    VwGolfMk4Tdi;

    val displayName: String
        get() = when (this) {
            Generic -> "Generic OBD"
            VwMqb -> "VW MQB (Nivus)"
            VwGolfMk4Tdi -> "VW Golf mk4 TDI (ASZ)"
        }

    companion object {
        val DEFAULT = Generic

        fun fromName(name: String): VehicleObdProfile =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
