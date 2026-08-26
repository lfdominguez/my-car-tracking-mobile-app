package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelLevelReadingTest {
    @Test
    fun `resolves SAE PID 2f in either case`() {
        assertEquals(12.5, FuelLevelReading.fromPidValues(mapOf("2f" to 12.5))!!, 0.001)
        assertEquals(12.5, FuelLevelReading.fromPidValues(mapOf("2F" to 12.5))!!, 0.001)
        assertEquals("2f", FuelLevelReading.sourceKey(mapOf("2f" to 12.5)))
    }

    @Test
    fun `null when absent`() {
        assertNull(FuelLevelReading.fromPidValues(emptyMap()))
        assertNull(FuelLevelReading.sourceKey(emptyMap()))
    }

    @Test
    fun `ignores the retired VW cluster fuel key`() {
        // DID 0x2206 was removed: never worked on the test vehicle, uncorroborated,
        // and reaching it broke Mode 01.
        assertNull(FuelLevelReading.fromPidValues(mapOf("ffuelpct" to 40.0)))
        assertEquals(
            12.5,
            FuelLevelReading.fromPidValues(mapOf("ffuelpct" to 40.0, "2f" to 12.5))!!,
            0.001,
        )
    }
}
