package com.domivega.gps_car.fuel

enum class FuelTypePreset(
    val displayName: String,
    val stoichAfr: Double?,
    val densityGl: Double?,
) {
    E0("E0 (gasoline)", 14.7, 745.0),
    E10("E10", 14.08, 745.0),
    E27("E27 (BR gasohol)", 13.2, 755.0),
    E100("E100 (ethanol)", 9.0, 789.0),
    CUSTOM("Custom", null, null);

    companion object {
        fun fromName(name: String): FuelTypePreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CUSTOM
    }
}
