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
}
