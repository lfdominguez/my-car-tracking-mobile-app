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
}
