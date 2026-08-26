package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelLevelReadingTest {
    @Test
    fun `prefers cluster ffuelpct when it agrees with Mode 01 2f`() {
        val v = FuelLevelReading.fromPidValues(
            mapOf(VwClusterDids.KEY_FUEL_PCT to 40.0, "2f" to 38.0),
        )
        assertEquals(40.0, v!!, 0.001)
        assertEquals(
            VwClusterDids.KEY_FUEL_PCT,
            FuelLevelReading.sourceKey(
                mapOf(VwClusterDids.KEY_FUEL_PCT to 40.0, "2f" to 38.0),
            ),
        )
    }

    @Test
    fun `cluster ffuelpct is used alone when 2f is absent`() {
        val v = FuelLevelReading.fromPidValues(mapOf(VwClusterDids.KEY_FUEL_PCT to 40.0))
        assertEquals(40.0, v!!, 0.001)
    }

    @Test
    fun `standard 2f wins when the unverified cluster DID disagrees`() {
        // DID 0x2206 is not corroborated publicly and every payload byte decodes to
        // a plausible percentage, so wide disagreement is the only available tell.
        val values = mapOf(VwClusterDids.KEY_FUEL_PCT to 40.0, "2f" to 10.0)
        assertEquals(10.0, FuelLevelReading.fromPidValues(values)!!, 0.001)
        assertEquals("2f", FuelLevelReading.sourceKey(values))
    }

    @Test
    fun `disagreement exactly at the tolerance still prefers cluster`() {
        val values = mapOf(
            VwClusterDids.KEY_FUEL_PCT to 40.0,
            "2f" to 40.0 - FuelLevelReading.AGREEMENT_TOLERANCE_PCT,
        )
        assertEquals(40.0, FuelLevelReading.fromPidValues(values)!!, 0.001)
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
