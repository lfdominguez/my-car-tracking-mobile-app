package com.domivega.gps_car.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelConsumptionCalculatorTest {
    private val e10Config = FuelCalcConfig(
        stoichAfr = 14.08,
        densityGl = 745.0,
        displacementL = 1.0,
        ve = 0.85,
    )

    @Test
    fun `maf path e10 lambda 1`() {
        // L/h = 10 * 3600 / (14.08 * 1.0 * 745) ≈ 3.433
        val result = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0),
        )
        assertNotNull(result)
        assertEquals(3.433, result!!, 0.01)
    }

    @Test
    fun `lambda richer increases fuel rate`() {
        val lean = FuelConsumptionCalculator.litersPerHour(
            e10Config, FuelCalcSensors(mafGs = 10.0, lambda = 1.0),
        )!!
        val rich = FuelConsumptionCalculator.litersPerHour(
            e10Config, FuelCalcSensors(mafGs = 10.0, lambda = 0.8),
        )!!
        assertTrue(rich > lean)
    }

    @Test
    fun `invalid lambda falls back to 1_0`() {
        val a = FuelConsumptionCalculator.litersPerHour(
            e10Config, FuelCalcSensors(mafGs = 5.0, lambda = 1.0),
        )
        val b = FuelConsumptionCalculator.litersPerHour(
            e10Config, FuelCalcSensors(mafGs = 5.0, lambda = 99.0),
        )
        assertEquals(a!!, b!!, 1e-9)
    }

    @Test
    fun `map fallback produces positive rate without maf`() {
        val result = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(
                mafGs = null,
                lambda = 1.0,
                mapKpa = 100.0,
                rpm = 2000.0,
                iatC = 25.0,
            ),
        )
        assertNotNull(result)
        assertTrue(result!! > 0.0)
    }

    @Test
    fun `returns null without usable sensors`() {
        assertNull(
            FuelConsumptionCalculator.litersPerHour(
                e10Config,
                FuelCalcSensors(),
            ),
        )
    }
}
