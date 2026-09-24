package com.domivega.gps_car.network

import com.domivega.gps_car.settings.SampleUploadFieldFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        accelPeakMps2 = 3.4,
        accelRmsMps2 = 2.8,
        deviceTiltDeltaDeg = 1.5,
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
    fun `motion flag clears all three motion fields together`() {
        // They are useless apart: a peak with no RMS cannot be told from a pothole.
        val flags = SampleUploadFieldFlags.ALL_ENABLED.copy(motion = false)
        val out = SampleFieldFilter.apply(baseSample(), flags)
        assertNull(out.accelPeakMps2)
        assertNull(out.accelRmsMps2)
        assertNull(out.deviceTiltDeltaDeg)
        // Car telemetry is untouched — motion is opt-out on its own.
        assertEquals(1.5, out.fuelConsumptionRate!!, 0.0)
    }

    @Test
    fun `motion fields survive when the flag is on`() {
        val out = SampleFieldFilter.apply(baseSample(), SampleUploadFieldFlags.ALL_ENABLED)
        assertEquals(3.4, out.accelPeakMps2!!, 0.0)
        assertEquals(2.8, out.accelRmsMps2!!, 0.0)
        assertEquals(1.5, out.deviceTiltDeltaDeg!!, 0.0)
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

    @Test
    fun `fault codes pass through whatever the flags`() {
        val withCodes = baseSample().copy(dtcCodes = listOf("P0133"), pendingDtcCodes = emptyList())
        val allOff = SampleUploadFieldFlags(
            fuelConsumptionRate = false,
            engineLoadPct = false,
            absoluteEngineLoadPct = false,
            shortTermFuelTrimPct = false,
            longTermFuelTrimPct = false,
            fuelLevelPct = false,
            acceleratorPedalPct = false,
            ambientAirTempC = false,
            odometerValueKm = false,
            engineCoolantTempC = false,
            manifoldAbsolutePressureKpa = false,
            controlModuleVoltage = false,
            engineOnTime = false,
            massAirFlow = false,
            lambdaCmd = false,
            atmosphericPressure = false,
            intakeAirTemperature = false,
            motion = false,
        )
        val out = SampleFieldFilter.apply(withCodes, allOff)
        assertEquals(listOf("P0133"), out.dtcCodes)
        assertEquals(emptyList<String>(), out.pendingDtcCodes)
    }

    @Test
    fun `fault code fields are omitted when not read and kept when empty`() {
        val json = kotlinx.serialization.json.Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val notRead = json.encodeToString(Sample.serializer(), baseSample())
        assertFalse(notRead.contains("dtc_codes"))
        assertFalse(notRead.contains("pending_dtc_codes"))

        val readNone = json.encodeToString(
            Sample.serializer(),
            baseSample().copy(dtcCodes = listOf("P0420"), pendingDtcCodes = emptyList()),
        )
        assertTrue(readNone.contains("\"dtc_codes\":[\"P0420\"]"))
        assertTrue(readNone.contains("\"pending_dtc_codes\":[]"))
    }
}
