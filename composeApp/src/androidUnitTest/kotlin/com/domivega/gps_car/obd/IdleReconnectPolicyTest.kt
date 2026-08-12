package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleReconnectPolicyTest {

    @Test
    fun `fallback delay bounds are 30s to 45s`() {
        assertEquals(30_000L, IdleReconnectPolicy.FALLBACK_MIN_MS)
        assertEquals(45_000L, IdleReconnectPolicy.FALLBACK_MAX_MS)
    }

    @Test
    fun `nextDelayMs stays within min and max for jitter extremes`() {
        assertEquals(IdleReconnectPolicy.FALLBACK_MIN_MS, IdleReconnectPolicy.nextDelayMs(0.0))
        assertEquals(IdleReconnectPolicy.FALLBACK_MAX_MS, IdleReconnectPolicy.nextDelayMs(1.0))
        val mid = IdleReconnectPolicy.nextDelayMs(0.5)
        assertTrue(mid in IdleReconnectPolicy.FALLBACK_MIN_MS..IdleReconnectPolicy.FALLBACK_MAX_MS)
    }

    @Test
    fun `nextDelayMs clamps random outside 0 to 1`() {
        assertEquals(IdleReconnectPolicy.FALLBACK_MIN_MS, IdleReconnectPolicy.nextDelayMs(-1.0))
        assertEquals(IdleReconnectPolicy.FALLBACK_MAX_MS, IdleReconnectPolicy.nextDelayMs(2.0))
    }

    @Test
    fun `presence observation only when API 31 plus BLE address and available`() {
        assertTrue(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 31,
                transportIsBle = true,
                hasDeviceAddress = true,
                presenceAvailable = true,
            ),
        )
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 30,
                transportIsBle = true,
                hasDeviceAddress = true,
                presenceAvailable = true,
            ),
        )
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 31,
                transportIsBle = false,
                hasDeviceAddress = true,
                presenceAvailable = true,
            ),
        )
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 31,
                transportIsBle = true,
                hasDeviceAddress = false,
                presenceAvailable = true,
            ),
        )
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 31,
                transportIsBle = true,
                hasDeviceAddress = true,
                presenceAvailable = false,
            ),
        )
    }
}
