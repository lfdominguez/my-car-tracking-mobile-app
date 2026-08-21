package com.domivega.gps_car.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelTypePresetTest {
    @Test
    fun `E10 preset has expected stoich and density`() {
        val p = FuelTypePreset.E10
        assertEquals(14.08, p.stoichAfr!!, 0.0001)
        assertEquals(745.0, p.densityGl!!, 0.0001)
    }

    @Test
    fun `fromName parses known and defaults unknown to CUSTOM`() {
        assertEquals(FuelTypePreset.E27, FuelTypePreset.fromName("E27"))
        assertEquals(FuelTypePreset.CUSTOM, FuelTypePreset.fromName("NOPE"))
    }

    @Test
    fun `CUSTOM has null afr density so UI keeps user values`() {
        assertNull(FuelTypePreset.CUSTOM.stoichAfr)
        assertNull(FuelTypePreset.CUSTOM.densityGl)
    }

    @Test
    fun `B7 preset has diesel stoich and density`() {
        val p = FuelTypePreset.B7
        assertEquals(14.5, p.stoichAfr!!, 0.0001)
        assertEquals(835.0, p.densityGl!!, 0.0001)
        assertEquals(FuelClass.DIESEL, p.fuelClass)
    }

    @Test
    fun `gradesFor gasoline includes ethanol and custom not B7`() {
        val names = FuelTypePreset.gradesFor(FuelClass.GASOLINE).map { it.name }
        assertEquals(listOf("E0", "E10", "E27", "E100", "CUSTOM"), names)
    }

    @Test
    fun `gradesFor diesel is B7 and custom`() {
        val names = FuelTypePreset.gradesFor(FuelClass.DIESEL).map { it.name }
        assertEquals(listOf("B7", "CUSTOM"), names)
    }

    @Test
    fun `fromName parses B7 and FuelClass defaults unknown to GASOLINE`() {
        assertEquals(FuelTypePreset.B7, FuelTypePreset.fromName("B7"))
        assertEquals(FuelClass.GASOLINE, FuelClass.fromName("NOPE"))
        assertEquals(FuelClass.DIESEL, FuelClass.fromName("diesel"))
    }

    @Test
    fun `defaultGrade is E10 gasoline and B7 diesel`() {
        assertEquals(FuelTypePreset.E10, FuelTypePreset.defaultGrade(FuelClass.GASOLINE))
        assertEquals(FuelTypePreset.B7, FuelTypePreset.defaultGrade(FuelClass.DIESEL))
    }
}
