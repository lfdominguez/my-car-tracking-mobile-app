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

    private val dieselConfig = FuelCalcConfig(
        stoichAfr = 14.5,
        densityGl = 835.0,
        displacementL = 1.9,
        ve = 0.85,
        isDiesel = true,
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

    private val corollaConfig = FuelCalcConfig(
        stoichAfr = 14.08,
        densityGl = 740.0,
        displacementL = 2.0,
        ve = 0.85,
    )

    @Test
    fun `peak air mass at idle rpm matches atmospheric 100pct VE`() {
        // 2.0 L × 650 rpm × 1.184 / 120
        val peak = FuelConsumptionCalculator.peakAirMassGs(2.0, 650.0)!!
        assertEquals(2.0 * 650.0 * 1.184 / 120.0, peak, 1e-9)
    }

    @Test
    fun `rejects peak-air idle MAF and uses max-plausible idle bound`() {
        // Trip 079acb97: stopped ~650 rpm, MAF ~13.68 g/s ≈ peak air (not real idle).
        val maf = 13.68
        val rpm = 650.0
        val peak = FuelConsumptionCalculator.peakAirMassGs(2.0, rpm)!!
        assertTrue(maf >= peak * 0.70)

        val air = FuelConsumptionCalculator.airMassGs(
            corollaConfig,
            FuelCalcSensors(mafGs = maf, rpm = rpm, speedKph = 0.0),
        )!!
        val expect = peak * corollaConfig.ve * FuelConsumptionCalculator.IDLE_MAP_FRACTION
        assertEquals(expect, air, 1e-9)
        assertTrue(air in 1.4..1.7)

        val lh = FuelConsumptionCalculator.litersPerHour(
            corollaConfig,
            FuelCalcSensors(mafGs = maf, rpm = rpm, speedKph = 0.0, lambda = 1.0),
        )!!
        // ~0.52 L/h bound — not the bogus ~4.7 L/h
        assertTrue(lh < 0.7 && lh > 0.4)
        assertTrue(
            !FuelConsumptionCalculator.isTrustedSensorMaf(
                corollaConfig,
                FuelCalcSensors(mafGs = maf, rpm = rpm, speedKph = 0.0),
            ),
        )
    }

    @Test
    fun `keeps realistic idle MAF from sister corolla trip`() {
        // c4c7335b: ~2.86 g/s idle is already plausible vs peak ~14 g/s.
        val air = FuelConsumptionCalculator.airMassGs(
            corollaConfig,
            FuelCalcSensors(mafGs = 2.86, rpm = 737.0, speedKph = 0.0),
        )!!
        assertEquals(2.86, air, 1e-9)
        assertTrue(
            FuelConsumptionCalculator.isTrustedSensorMaf(
                corollaConfig,
                FuelCalcSensors(mafGs = 2.86, rpm = 737.0, speedKph = 0.0),
            ),
        )
    }

    @Test
    fun `does not sanitize peak-like MAF while moving`() {
        val air = FuelConsumptionCalculator.airMassGs(
            corollaConfig,
            FuelCalcSensors(mafGs = 13.68, rpm = 650.0, speedKph = 30.0),
        )!!
        assertEquals(13.68, air, 1e-9)
    }

    @Test
    fun `peak-air idle prefers MAP estimate over idle bound when MAP present`() {
        val mapAir = FuelConsumptionCalculator.airMassGs(
            corollaConfig,
            FuelCalcSensors(
                mafGs = null,
                mapKpa = 30.0,
                rpm = 650.0,
                iatC = 25.0,
            ),
        )!!
        val air = FuelConsumptionCalculator.airMassGs(
            corollaConfig,
            FuelCalcSensors(
                mafGs = 13.68,
                mapKpa = 30.0,
                rpm = 650.0,
                iatC = 25.0,
                speedKph = 0.0,
            ),
        )!!
        assertEquals(mapAir, air, 1e-9)
        assertTrue(air < 5.0)
    }

    @Test
    fun `null speed treated as stopped for idle peak-air detect`() {
        val peak = FuelConsumptionCalculator.peakAirMassGs(2.0, 650.0)!!
        val air = FuelConsumptionCalculator.airMassGs(
            corollaConfig,
            FuelCalcSensors(mafGs = 13.68, rpm = 650.0, speedKph = null),
        )!!
        assertEquals(peak * 0.85 * FuelConsumptionCalculator.IDLE_MAP_FRACTION, air, 1e-9)
    }

    @Test
    fun `dieselAfr idle load blends toward 38`() {
        assertEquals(38.0, FuelConsumptionCalculator.dieselAfr(0.0), 0.001)
        assertEquals(27.75, FuelConsumptionCalculator.dieselAfr(12.5), 0.001)
        assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(25.0), 0.001)
    }

    @Test
    fun `dieselAfr high or unknown load is cruise 17_5`() {
        assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(80.0), 0.001)
        assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(null), 0.001)
        assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(Double.NaN), 0.001)
    }

    @Test
    fun `diesel idle load uses leaner AFR than cruise load`() {
        val idle = FuelConsumptionCalculator.litersPerHour(
            dieselConfig,
            FuelCalcSensors(mafGs = 6.0, calculatedLoadPct = 10.0),
        )!!
        val cruise = FuelConsumptionCalculator.litersPerHour(
            dieselConfig,
            FuelCalcSensors(mafGs = 6.0, calculatedLoadPct = 40.0),
        )!!
        assertTrue(idle < cruise)
        // cruise: 6*3600/(17.5*835) ≈ 1.480
        assertEquals(1.480, cruise, 0.01)
    }

    @Test
    fun `diesel ignores gasoline lambda and fuel trims`() {
        val base = FuelConsumptionCalculator.litersPerHour(
            dieselConfig,
            FuelCalcSensors(mafGs = 10.0, calculatedLoadPct = 50.0),
        )!!
        val withLambda = FuelConsumptionCalculator.litersPerHour(
            dieselConfig,
            FuelCalcSensors(mafGs = 10.0, calculatedLoadPct = 50.0, lambda = 0.8, stftPct = 10.0),
        )!!
        assertEquals(base, withLambda, 1e-9)
    }

    @Test
    fun `diesel still prefers ECU PID 5E`() {
        val result = FuelConsumptionCalculator.litersPerHour(
            dieselConfig,
            FuelCalcSensors(mafGs = 10.0, calculatedLoadPct = 50.0, ecuFuelRateLh = 8.0),
        )
        assertEquals(8.0, result!!, 1e-9)
    }

    @Test
    fun `gasoline path unchanged when isDiesel false`() {
        val result = FuelConsumptionCalculator.litersPerHour(
            e10Config,
            FuelCalcSensors(mafGs = 10.0, lambda = 1.0, calculatedLoadPct = 10.0),
        )
        assertEquals(3.433, result!!, 0.01)
    }
}
