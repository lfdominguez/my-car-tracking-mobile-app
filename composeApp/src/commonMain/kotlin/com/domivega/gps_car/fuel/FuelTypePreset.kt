package com.domivega.gps_car.fuel

enum class FuelClass(
    val displayName: String,
) {
    GASOLINE("Gasoline"),
    DIESEL("Diesel");

    companion object {
        fun fromName(name: String): FuelClass =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GASOLINE
    }
}

enum class FuelTypePreset(
    val displayName: String,
    val stoichAfr: Double?,
    val densityGl: Double?,
    val fuelClass: FuelClass?,
) {
    E0("E0 (gasoline)", 14.7, 745.0, FuelClass.GASOLINE),
    E10("E10", 14.08, 745.0, FuelClass.GASOLINE),
    E27("E27 (BR gasohol)", 13.2, 755.0, FuelClass.GASOLINE),
    E100("E100 (ethanol)", 9.0, 789.0, FuelClass.GASOLINE),
    B7("B7 (diesel)", 14.5, 835.0, FuelClass.DIESEL),
    CUSTOM("Custom", null, null, null);

    companion object {
        fun fromName(name: String): FuelTypePreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CUSTOM

        fun gradesFor(fuelClass: FuelClass): List<FuelTypePreset> =
            entries.filter { it == CUSTOM || it.fuelClass == fuelClass }

        fun defaultGrade(fuelClass: FuelClass): FuelTypePreset =
            if (fuelClass == FuelClass.DIESEL) B7 else E10
    }
}
