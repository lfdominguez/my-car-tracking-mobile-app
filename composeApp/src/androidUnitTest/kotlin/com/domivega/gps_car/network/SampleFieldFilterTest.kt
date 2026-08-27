package com.domivega.gps_car.network

import com.domivega.gps_car.settings.SampleUploadFieldFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SampleFieldFilterTest {

    private fun baseSample() = Sample(
        trackingId = "t1",
        recordedAt = 1L,
        lat = 1.0,
        lon = 2.0,
        acc = 3.0,
        vehicleEngineRpm = 800.0,
        vehicleSpeedKph = 40.0,
        fuelConsumptionRate = 1.5,
        engineLoadPct = 20.0,
        absoluteEngineLoadPct = 25.0,
        shortTermFuelTrimPct = 1.0,
        longTermFuelTrimPct = 2.0,
        fuelLevelPct = 50.0,
        acceleratorPedalPct = 10.0,
        ambientAirTempC = 22.0,
        odometerValueKm = 1000.0,
        engineCoolantTempC = 90.0,
        manifoldAbsolutePressureKpa = 100.0,
        controlModuleVoltage = 14.0,
        engineOnTime = 60.0,
        massAirFlow = 5.0,
        lambdaCmd = 1.0,
        atmosphericPressure = 101.0,
        intakeAirTemperature = 30.0,
    )

    @Test
    fun `all flags on leaves sample unchanged`() {
        val s = baseSample()
        val out = SampleFieldFilter.apply(s, SampleUploadFieldFlags.ALL_ENABLED)
        assertEquals(s, out)
    }

    @Test
    fun `disabled optional field becomes null`() {
        val flags = SampleUploadFieldFlags.ALL_ENABLED.copy(fuelConsumptionRate = false)
        val out = SampleFieldFilter.apply(baseSample(), flags)
        assertNull(out.fuelConsumptionRate)
        assertEquals(800.0, out.vehicleEngineRpm)
        assertEquals(40.0, out.vehicleSpeedKph)
        assertEquals(1.0, out.lat!!, 0.0)
        assertNotNull(out.engineLoadPct)
    }

    @Test
    fun `always-on fields never cleared`() {
        val out = SampleFieldFilter.apply(baseSample(), SampleUploadFieldFlags.ALL_ENABLED)
        assertEquals(1.0, out.lat!!, 0.0)
        assertEquals(2.0, out.lon!!, 0.0)
        assertEquals(3.0, out.acc!!, 0.0)
        assertEquals(800.0, out.vehicleEngineRpm)
        assertEquals(40.0, out.vehicleSpeedKph)
    }
}
