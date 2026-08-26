package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalHeaderPolicyTest {

    @Test
    fun probePid_alternatesSoOneMutePidCannotDecideTheSession() {
        assertEquals("0c", PhysicalHeaderPolicy.probePidHex(0))
        assertEquals("05", PhysicalHeaderPolicy.probePidHex(1))
        assertEquals("0c", PhysicalHeaderPolicy.probePidHex(2))
        assertEquals("05", PhysicalHeaderPolicy.probePidHex(3))
    }

    @Test
    fun probeBackoff_growsPerAttempt() {
        assertEquals(250L, PhysicalHeaderPolicy.probeBackoffMs(0))
        assertEquals(500L, PhysicalHeaderPolicy.probeBackoffMs(1))
        assertEquals(750L, PhysicalHeaderPolicy.probeBackoffMs(2))
    }

    @Test
    fun receiveFilter_skippedOnVwMqbBecauseTheClusterHopOwnsAtcra() {
        assertFalse(PhysicalHeaderPolicy.shouldApplyReceiveFilter(VehicleObdProfile.VwMqb))
        assertTrue(PhysicalHeaderPolicy.shouldApplyReceiveFilter(VehicleObdProfile.Generic))
        assertTrue(PhysicalHeaderPolicy.shouldApplyReceiveFilter(VehicleObdProfile.VwGolfMk4Tdi))
    }

    @Test
    fun reprobe_onlyWhenStuckOnFunctionalHeader() {
        assertFalse(
            PhysicalHeaderPolicy.shouldReprobe(
                round = 100,
                lastProbeRound = 0,
                onFunctionalHeader = false,
                engineOkCount = 50,
            )
        )
    }

    @Test
    fun reprobe_waitsForTheBusToProveItIsAlive() {
        assertFalse(
            PhysicalHeaderPolicy.shouldReprobe(
                round = 100,
                lastProbeRound = 0,
                onFunctionalHeader = true,
                engineOkCount = PhysicalHeaderPolicy.REPROBE_MIN_ENGINE_OK - 1,
            )
        )
        assertTrue(
            PhysicalHeaderPolicy.shouldReprobe(
                round = 100,
                lastProbeRound = 0,
                onFunctionalHeader = true,
                engineOkCount = PhysicalHeaderPolicy.REPROBE_MIN_ENGINE_OK,
            )
        )
    }

    @Test
    fun reprobe_honoursTheRoundCadence() {
        val every = PhysicalHeaderPolicy.REPROBE_EVERY_ROUNDS
        assertFalse(
            PhysicalHeaderPolicy.shouldReprobe(
                round = 30 + every - 1,
                lastProbeRound = 30,
                onFunctionalHeader = true,
                engineOkCount = 99,
            )
        )
        assertTrue(
            PhysicalHeaderPolicy.shouldReprobe(
                round = 30 + every,
                lastProbeRound = 30,
                onFunctionalHeader = true,
                engineOkCount = 99,
            )
        )
    }
}
