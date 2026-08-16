package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleOnPolicyTest {

    @Test
    fun `no proof is not vehicle on`() {
        assertFalse(VehicleOnPolicy.isVehicleOn(VehicleOnPolicy.State(), nowMs = 1_000L))
    }

    @Test
    fun `voltage is a proof before positive RPM`() {
        val next = VehicleOnPolicy.onVoltage(VehicleOnPolicy.State(), nowMs = 5_000L)
        assertFalse(next.sawPositiveRpm)
        assertEquals(5_000L, next.lastProofAtMs)
        assertTrue(VehicleOnPolicy.isVehicleOn(next, nowMs = 5_000L))
    }

    @Test
    fun `voltage is not a proof after positive RPM`() {
        val running = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        val afterVoltage = VehicleOnPolicy.onVoltage(running, nowMs = 20_000L)
        assertTrue(afterVoltage.sawPositiveRpm)
        assertEquals(1_000L, afterVoltage.lastProofAtMs)
        assertFalse(VehicleOnPolicy.isVehicleOn(afterVoltage, nowMs = 20_000L))
    }

    @Test
    fun `zero RPM is never a proof`() {
        val afterZero = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 0.0, nowMs = 1_000L)
        assertFalse(afterZero.sawPositiveRpm)
        assertNull(afterZero.lastProofAtMs)
        val afterRun = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 900.0, nowMs = 1_000L)
        val stillZero = VehicleOnPolicy.onRpm(afterRun, rpm = 0.0, nowMs = 2_000L)
        assertTrue(stillZero.sawPositiveRpm)
        assertEquals(1_000L, stillZero.lastProofAtMs)
    }

    @Test
    fun `positive RPM is a proof and sets the flag`() {
        val next = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 750.0, nowMs = 3_000L)
        assertTrue(next.sawPositiveRpm)
        assertEquals(3_000L, next.lastProofAtMs)
        assertTrue(VehicleOnPolicy.isVehicleOn(next, nowMs = 3_000L))
    }

    @Test
    fun `positive speed is a proof even after ICE ran`() {
        val running = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        val moving = VehicleOnPolicy.onSpeed(running, speedKph = 12.0, nowMs = 50_000L)
        assertTrue(moving.sawPositiveRpm)
        assertEquals(50_000L, moving.lastProofAtMs)
        assertTrue(VehicleOnPolicy.isVehicleOn(moving, nowMs = 50_000L))
    }

    @Test
    fun `zero speed is not a proof`() {
        val running = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        val stopped = VehicleOnPolicy.onSpeed(running, speedKph = 0.0, nowMs = 2_000L)
        assertEquals(1_000L, stopped.lastProofAtMs)
    }

    @Test
    fun `proof older than stale window is not vehicle on`() {
        val next = VehicleOnPolicy.onVoltage(VehicleOnPolicy.State(), nowMs = 1_000L)
        assertTrue(VehicleOnPolicy.isVehicleOn(next, nowMs = 1_000L + 9_999L))
        assertFalse(VehicleOnPolicy.isVehicleOn(next, nowMs = 1_000L + 10_000L))
    }

    @Test
    fun `session reset clears last proof but can keep sawPositiveRpm`() {
        val running = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        val reset = VehicleOnPolicy.onSessionReset(sawPositiveRpm = running.sawPositiveRpm)
        assertTrue(reset.sawPositiveRpm)
        assertNull(reset.lastProofAtMs)
        assertFalse(VehicleOnPolicy.isVehicleOn(reset, nowMs = 2_000L))
    }

    @Test
    fun `GPS speed is allowed only when OBD speed never decoded and ECU is live`() {
        assertTrue(
            VehicleOnPolicy.shouldAcceptGpsSpeed(
                obdSpeedDecoded = false,
                ecuConnected = true,
            ),
        )
        assertFalse(
            VehicleOnPolicy.shouldAcceptGpsSpeed(
                obdSpeedDecoded = true,
                ecuConnected = true,
            ),
        )
        assertFalse(
            VehicleOnPolicy.shouldAcceptGpsSpeed(
                obdSpeedDecoded = false,
                ecuConnected = false,
            ),
        )
    }

    @Test
    fun `link lost clears proof clock so vehicle is immediately off`() {
        val running = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        val lost = VehicleOnPolicy.onLinkLost(running)
        assertTrue(lost.sawPositiveRpm)
        assertNull(lost.lastProofAtMs)
        assertFalse(VehicleOnPolicy.isVehicleOn(lost, nowMs = 1_001L))
    }

    @Test
    fun `fresh live PID applies the matching proof`() {
        val voltage = VehicleOnPolicy.applyFreshPid(
            VehicleOnPolicy.State(),
            pid = "42",
            value = 12.4,
            nowMs = 4_000L,
        )
        assertEquals(4_000L, voltage.lastProofAtMs)
        assertFalse(voltage.sawPositiveRpm)

        val rpm = VehicleOnPolicy.applyFreshPid(voltage, pid = "0C", value = 800.0, nowMs = 5_000L)
        assertTrue(rpm.sawPositiveRpm)
        assertEquals(5_000L, rpm.lastProofAtMs)

        val ignoredVoltage = VehicleOnPolicy.applyFreshPid(rpm, pid = "42", value = 12.1, nowMs = 6_000L)
        assertEquals(5_000L, ignoredVoltage.lastProofAtMs)

        val speed = VehicleOnPolicy.applyFreshPid(ignoredVoltage, pid = "0d", value = 15.0, nowMs = 7_000L)
        assertEquals(7_000L, speed.lastProofAtMs)

        val other = VehicleOnPolicy.applyFreshPid(speed, pid = "04", value = 20.0, nowMs = 8_000L)
        assertEquals(7_000L, other.lastProofAtMs)
    }
}
