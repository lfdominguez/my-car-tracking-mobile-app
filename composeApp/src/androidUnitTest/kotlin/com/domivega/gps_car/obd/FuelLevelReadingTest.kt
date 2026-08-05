package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelLevelReadingTest {
    @Test
    fun `prefers cluster ffuelpct over Mode 01 2f`() {
        val v = FuelLevelReading.fromPidValues(
            mapOf(VwClusterDids.KEY_FUEL_PCT to 40.0, "2f" to 10.0),
        )
        assertEquals(40.0, v!!, 0.001)
    }

    @Test
    fun `falls back to 2f`() {
        assertEquals(12.5, FuelLevelReading.fromPidValues(mapOf("2f" to 12.5))!!, 0.001)
        assertEquals(12.5, FuelLevelReading.fromPidValues(mapOf("2F" to 12.5))!!, 0.001)
    }

    @Test
    fun `null when absent`() {
        assertNull(FuelLevelReading.fromPidValues(emptyMap()))
    }
}
