package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleOnPolicyTest {

    @Test
    fun `trip end while still moving does not park the adapter`() {
        // Measured on an MG3 Hybrid+: two trips ended mid-drive at 20-25 km/h on a
        // 13.6 V bus. Parking the adapter there cost a 300 s blind window and split
        // one drive into separate trips exactly 400 s apart.
        val moving = VehicleOnPolicy.State(lastSpeedKph = 21.0, lastVoltage = 13.628)
        assertFalse(VehicleOnPolicy.mayParkAdapterAfterTripEnd(moving))
    }

    @Test
    fun `trip end on a charging bus does not park the adapter`() {
        val charging = VehicleOnPolicy.State(lastSpeedKph = 0.0, lastVoltage = 13.9)
        assertFalse(VehicleOnPolicy.mayParkAdapterAfterTripEnd(charging))
    }

    @Test
    fun `trip end stopped on a non-charging bus parks the adapter`() {
        // The same car's genuine parking ends: speed 0, bus fallen off charge.
        val parked = VehicleOnPolicy.State(lastSpeedKph = 0.0, lastVoltage = 12.928)
        assertTrue(VehicleOnPolicy.mayParkAdapterAfterTripEnd(parked))
    }

    @Test
    fun `trip end with nothing known parks the adapter`() {
        // Link lost before any decode — no evidence of motion, so the battery-saving
        // sleep stays the default.
        assertTrue(VehicleOnPolicy.mayParkAdapterAfterTripEnd(VehicleOnPolicy.State()))
    }

    @Test
    fun `no proof is not vehicle on`() {
        assertFalse(VehicleOnPolicy.isVehicleOn(VehicleOnPolicy.State(), nowMs = 1_000L))
    }

    @Test
    fun `voltage is a proof before positive RPM`() {
        val next = VehicleOnPolicy.onVoltage(VehicleOnPolicy.State(), volts = 12.4, nowMs = 5_000L)
        assertFalse(next.sawPositiveRpm)
        assertEquals(5_000L, next.lastProofAtMs)
        assertTrue(VehicleOnPolicy.isVehicleOn(next, nowMs = 5_000L))
    }

    @Test
    fun `voltage is not a proof after positive RPM`() {
        val running = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        val afterVoltage = VehicleOnPolicy.onVoltage(running, volts = 13.8, nowMs = 20_000L)
        assertTrue(afterVoltage.sawPositiveRpm)
        assertEquals(1_000L, afterVoltage.lastProofAtMs)
        assertFalse(VehicleOnPolicy.isVehicleOn(afterVoltage, nowMs = 20_000L))
    }

    @Test
    fun `hybrid voltage stays a proof after RPM`() {
        val running = VehicleOnPolicy.onRpm(
            VehicleOnPolicy.State(),
            rpm = 800.0,
            nowMs = 1_000L,
            powertrain = PowertrainKind.HYBRID,
        )
        val evMode = VehicleOnPolicy.onRpm(running, rpm = 0.0, nowMs = 2_000L, PowertrainKind.HYBRID)
        val afterVoltage = VehicleOnPolicy.onVoltage(
            evMode,
            volts = 13.8,
            nowMs = 3_000L,
            powertrain = PowertrainKind.HYBRID,
        )
        assertTrue(VehicleOnPolicy.isVehicleOn(afterVoltage, nowMs = 3_000L))
    }

    @Test
    fun `electric ignores RPM as on proof`() {
        val rpm = VehicleOnPolicy.onRpm(
            VehicleOnPolicy.State(),
            rpm = 900.0,
            nowMs = 1_000L,
            powertrain = PowertrainKind.ELECTRIC,
        )
        assertFalse(VehicleOnPolicy.isVehicleOn(rpm, nowMs = 1_000L))
        val volts = VehicleOnPolicy.onVoltage(rpm, volts = 13.5, nowMs = 2_000L, PowertrainKind.ELECTRIC)
        assertTrue(VehicleOnPolicy.isVehicleOn(volts, nowMs = 2_000L))
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
        val next = VehicleOnPolicy.onVoltage(VehicleOnPolicy.State(), volts = 12.4, nowMs = 1_000L)
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
        // Rest voltage without a decoded speed is not parked-off (cranking).
        assertEquals(5_000L, ignoredVoltage.lastProofAtMs)

        val speed = VehicleOnPolicy.applyFreshPid(ignoredVoltage, pid = "0d", value = 15.0, nowMs = 7_000L)
        assertEquals(7_000L, speed.lastProofAtMs)

        val other = VehicleOnPolicy.applyFreshPid(speed, pid = "04", value = 20.0, nowMs = 8_000L)
        assertEquals(7_000L, other.lastProofAtMs)

        val runtime = VehicleOnPolicy.applyFreshPid(
            VehicleOnPolicy.onSessionReset(sawPositiveRpm = true),
            pid = "1F",
            value = 3.0,
            nowMs = 9_000L,
        )
        val rising = VehicleOnPolicy.applyFreshPid(runtime, pid = "1f", value = 4.0, nowMs = 10_000L)
        assertEquals(10_000L, rising.lastProofAtMs)
    }

    @Test
    fun `after ICE rest battery and fake idle RPM is not vehicle on`() {
        // Friend's parked ghosts: RPM 704 + 12.49 V + speed 0 after a real ICE trip.
        var state = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 13.8, nowMs = 1_100L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 90_000L)
        state = VehicleOnPolicy.onSessionReset(sawPositiveRpm = state.sawPositiveRpm)

        state = VehicleOnPolicy.onRpm(state, rpm = 704.0, nowMs = 100_000L)
        assertTrue(state.sawPositiveRpm)
        assertNull(state.lastProofAtMs)

        state = VehicleOnPolicy.onVoltage(state, volts = 12.49, nowMs = 100_200L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 100_300L)
        state = VehicleOnPolicy.onRpm(state, rpm = 704.0, nowMs = 100_400L)

        assertFalse(VehicleOnPolicy.isVehicleOn(state, nowMs = 100_400L))
        assertFalse(VehicleOnPolicy.shouldReleaseAdapter(state, nowMs = 100_400L))
        assertTrue(
            VehicleOnPolicy.shouldReleaseAdapter(
                state,
                nowMs = 100_300L + VehicleOnPolicy.PARKED_CONFIRM_MS,
            ),
        )
    }

    @Test
    fun `after ICE charging voltage keeps idle RPM as vehicle on`() {
        var state = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 800.0, nowMs = 1_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 13.8, nowMs = 1_100L)
        state = VehicleOnPolicy.onRpm(state, rpm = 720.0, nowMs = 50_000L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 50_100L)
        assertTrue(VehicleOnPolicy.isVehicleOn(state, nowMs = 50_100L))
        assertFalse(VehicleOnPolicy.shouldReleaseAdapter(state, nowMs = 50_100L))
    }

    @Test
    fun `after ICE RPM without a voltage reading does not start a trip`() {
        val ice = VehicleOnPolicy.onSessionReset(sawPositiveRpm = true)
        val rpmOnly = VehicleOnPolicy.onRpm(ice, rpm = 704.0, nowMs = 10_000L)
        assertTrue(rpmOnly.sawPositiveRpm)
        assertNull(rpmOnly.lastProofAtMs)
        assertFalse(VehicleOnPolicy.isVehicleOn(rpmOnly, nowMs = 10_000L))
        assertFalse(
            VehicleOnPolicy.shouldReleaseAdapter(
                rpmOnly,
                nowMs = 10_000L + VehicleOnPolicy.PARKED_CONFIRM_MS,
            ),
        )
    }

    @Test
    fun `rest voltage after ICE clears proof even if RPM stays positive`() {
        var state = VehicleOnPolicy.onRpm(VehicleOnPolicy.State(), rpm = 900.0, nowMs = 1_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 13.7, nowMs = 1_100L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 2_000L)
        assertTrue(VehicleOnPolicy.isVehicleOn(state, nowMs = 2_000L))

        state = VehicleOnPolicy.onVoltage(state, volts = 12.49, nowMs = 3_000L)
        assertNull(state.lastProofAtMs)
        assertFalse(VehicleOnPolicy.isVehicleOn(state, nowMs = 3_000L))
        assertTrue(VehicleOnPolicy.shouldReleaseAdapter(state, nowMs = 3_000L + VehicleOnPolicy.PARKED_CONFIRM_MS))
    }

    @Test
    fun `cranking after ICE with rest voltage is not immediate parked release`() {
        // Friend started the car while the previous ICE flag was still persisted.
        // Alternator has not raised voltage yet; speed is 0. Must not sleep the dongle.
        var state = VehicleOnPolicy.onSessionReset(sawPositiveRpm = true)
        state = VehicleOnPolicy.onRpm(state, rpm = 700.0, nowMs = 10_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 12.4, nowMs = 10_100L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 10_200L)
        assertFalse(VehicleOnPolicy.isVehicleOn(state, nowMs = 10_200L))
        assertFalse(VehicleOnPolicy.shouldReleaseAdapter(state, nowMs = 10_200L))
    }

    @Test
    fun `rest voltage and RPM without decoded speed is not parked release`() {
        var state = VehicleOnPolicy.onSessionReset(sawPositiveRpm = true)
        state = VehicleOnPolicy.onRpm(state, rpm = 704.0, nowMs = 10_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 12.49, nowMs = 10_100L)
        assertFalse(
            VehicleOnPolicy.shouldReleaseAdapter(
                state,
                nowMs = 10_100L + VehicleOnPolicy.PARKED_CONFIRM_MS,
            ),
        )
    }

    @Test
    fun `increasing engine runtime is a vehicle-on proof after ICE`() {
        var state = VehicleOnPolicy.onSessionReset(sawPositiveRpm = true)
        state = VehicleOnPolicy.onRpm(state, rpm = 700.0, nowMs = 10_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 12.4, nowMs = 10_100L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 10_200L)
        state = VehicleOnPolicy.onRuntime(state, seconds = 1.0, nowMs = 10_300L)
        state = VehicleOnPolicy.onRuntime(state, seconds = 2.0, nowMs = 11_300L)
        assertEquals(11_300L, state.lastProofAtMs)
        assertTrue(VehicleOnPolicy.isVehicleOn(state, nowMs = 11_300L))
        assertFalse(VehicleOnPolicy.shouldReleaseAdapter(state, nowMs = 11_300L))
    }

    @Test
    fun `stale constant engine runtime is not a proof and parked rest can release after confirm`() {
        var state = VehicleOnPolicy.onSessionReset(sawPositiveRpm = true)
        state = VehicleOnPolicy.onRpm(state, rpm = 704.0, nowMs = 100_000L)
        state = VehicleOnPolicy.onVoltage(state, volts = 12.49, nowMs = 100_200L)
        state = VehicleOnPolicy.onSpeed(state, speedKph = 0.0, nowMs = 100_300L)
        state = VehicleOnPolicy.onRuntime(state, seconds = 1_234.0, nowMs = 100_400L)
        state = VehicleOnPolicy.onRuntime(state, seconds = 1_234.0, nowMs = 101_400L)
        assertFalse(VehicleOnPolicy.isVehicleOn(state, nowMs = 101_400L))
        assertFalse(VehicleOnPolicy.shouldReleaseAdapter(state, nowMs = 101_400L))
        assertTrue(
            VehicleOnPolicy.shouldReleaseAdapter(
                state,
                nowMs = 100_300L + VehicleOnPolicy.PARKED_CONFIRM_MS,
            ),
        )
    }
}
