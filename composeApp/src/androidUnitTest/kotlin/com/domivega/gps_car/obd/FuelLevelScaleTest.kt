package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelLevelScaleTest {

    /** The SAE decode of the raw byte the ECU actually sent. */
    private fun sae(raw: Int): Double = raw * 100.0 / 255.0

    @Test
    fun `profiles without a declared full-tank raw are relayed unchanged`() {
        assertNull(FuelLevelScale.fullTankRawFor(VehicleObdProfile.Generic))
        assertNull(FuelLevelScale.fullTankRawFor(VehicleObdProfile.VwMqb))
        assertNull(FuelLevelScale.fullTankRawFor(VehicleObdProfile.VwGolfMk4Tdi))
        assertEquals(
            sae(181),
            FuelLevelScale.correctedPct(sae(181), VehicleObdProfile.Generic),
            0.0001,
        )
    }

    @Test
    fun `MG3 full-tank raw reads as a full tank`() {
        // The measured brim: raw 181, dash reading full, previously shown as 70.98 %.
        assertEquals(70.9804, sae(181), 0.0001)
        assertEquals(
            100.0,
            FuelLevelScale.correctedPct(sae(181), VehicleObdProfile.Mg3HybridPlus),
            0.0001,
        )
    }

    @Test
    fun `MG3 scale is linear from empty through the pre-refuel readings`() {
        assertEquals(0.0, FuelLevelScale.correctedPct(0.0, VehicleObdProfile.Mg3HybridPlus), 0.0001)
        // The logged pre-refuel pair, 45 and 43 raw, becomes a quarter of a tank.
        assertEquals(
            24.862,
            FuelLevelScale.correctedPct(sae(45), VehicleObdProfile.Mg3HybridPlus),
            0.001,
        )
        assertEquals(
            23.757,
            FuelLevelScale.correctedPct(sae(43), VehicleObdProfile.Mg3HybridPlus),
            0.001,
        )
    }

    @Test
    fun `readings above the declared full-tank raw clamp instead of exceeding 100`() {
        // Nothing above raw 181 was ever observed, but a sender that does go higher
        // must not produce a tank that is 110 % full and fail the range gate.
        assertEquals(
            100.0,
            FuelLevelScale.correctedPct(sae(220), VehicleObdProfile.Mg3HybridPlus),
            0.0001,
        )
        assertEquals(
            100.0,
            FuelLevelScale.correctedPct(100.0, VehicleObdProfile.Mg3HybridPlus),
            0.0001,
        )
    }

    @Test
    fun `non-finite input is passed through untouched`() {
        assertEquals(
            Double.NaN,
            FuelLevelScale.correctedPct(Double.NaN, VehicleObdProfile.Mg3HybridPlus),
            0.0,
        )
    }
}
