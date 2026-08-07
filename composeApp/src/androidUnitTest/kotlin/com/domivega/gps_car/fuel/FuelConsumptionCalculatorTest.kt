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
    fun `airMassGs prefers sensor maf over map estimate`() {
        val sensors = FuelCalcSensors(
            mafGs = 12.5,
            mapKpa = 100.0,
            rpm = 2000.0,
            iatC = 25.0,
        )
        assertEquals(12.5, FuelConsumptionCalculator.airMassGs(e10Config, sensors)!!, 1e-9)
    }

    @Test
    fun `airMassGs estimates from map when maf missing`() {
        val air = FuelConsumptionCalculator.airMassGs(
            e10Config,
            FuelCalcSensors(
                mafGs = null,
                mapKpa = 100.0,
                rpm = 2000.0,
                iatC = 25.0,
            ),
        )
        assertNotNull(air)
        assertTrue(air!! > 0.0)
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

    @Test
    fun `ecu fuel rate PID 5E wins over maf path`() {
        val result = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0, ecuFuelRateLh = 12.0),
        )
        assertEquals(12.0, result!!, 1e-9)
    }

    @Test
    fun `invalid ecu fuel rate falls back to maf`() {
        val mafOnly = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0),
        )!!
        val badEcu = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0, ecuFuelRateLh = 0.0),
        )!!
        assertEquals(mafOnly, badEcu, 1e-9)
    }

    @Test
    fun `positive trims increase estimated fuel rate`() {
        val base = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0),
        )!!
        val trimmed = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0, stftPct = 10.0, ltftPct = 5.0),
        )!!
        assertEquals(base * 1.15, trimmed, 0.01)
    }

    @Test
    fun `out of range trim ignored`() {
        val base = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0),
        )!!
        val withBad = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0, stftPct = 50.0),
        )!!
        assertEquals(base, withBad, 1e-9)
    }

    @Test
    fun `trim factor clamps at 1_3`() {
        assertEquals(1.3, FuelConsumptionCalculator.trimFactor(25.0, 25.0), 1e-9)
        assertEquals(0.7, FuelConsumptionCalculator.trimFactor(-25.0, -25.0), 1e-9)
    }
}
