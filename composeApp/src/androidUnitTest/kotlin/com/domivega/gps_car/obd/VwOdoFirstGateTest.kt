package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VwOdoFirstGateTest {

    @Test
    fun `requires odometer before Mode 01 only for VwMqb`() {
        assertTrue(VwOdoFirstGate.requiresOdometerBeforeMode01(VehicleObdProfile.VwMqb))
        assertFalse(VwOdoFirstGate.requiresOdometerBeforeMode01(VehicleObdProfile.Generic))
        assertFalse(VwOdoFirstGate.requiresOdometerBeforeMode01(VehicleObdProfile.VwGolfMk4Tdi))
    }

    @Test
    fun `retry delay is two seconds and clamps negative attempts`() {
        assertEquals(2_000L, VwOdoFirstGate.retryDelayMs(0))
        assertEquals(2_000L, VwOdoFirstGate.retryDelayMs(5))
        assertEquals(2_000L, VwOdoFirstGate.retryDelayMs(-1))
    }

    @Test
    fun `recent lock survives a re-init, stale or missing one does not`() {
        val now = 10_000_000L
        assertTrue(VwOdoFirstGate.keepLockAcrossReinit(locked = true, lastLiveAtMs = now - 45_000L, nowMs = now))
        assertTrue(
            VwOdoFirstGate.keepLockAcrossReinit(true, now - VwOdoFirstGate.LOCK_CARRY_MS, now),
        )
        assertFalse(
            VwOdoFirstGate.keepLockAcrossReinit(true, now - VwOdoFirstGate.LOCK_CARRY_MS - 1, now),
        )
        assertFalse(VwOdoFirstGate.keepLockAcrossReinit(locked = false, lastLiveAtMs = now, nowMs = now))
        assertFalse(VwOdoFirstGate.keepLockAcrossReinit(locked = true, lastLiveAtMs = 0L, nowMs = now))
    }
}
