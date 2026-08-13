package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IdleReconnectPolicyTest {

    @Test
    fun `idle reconnect interval is one minute`() {
        assertEquals(60_000L, IdleReconnectPolicy.INTERVAL_MS)
        assertEquals(60_000L, IdleReconnectPolicy.FALLBACK_MIN_MS)
        assertEquals(60_000L, IdleReconnectPolicy.FALLBACK_MAX_MS)
    }

    @Test
    fun `nextDelayMs is always one minute`() {
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(0.0))
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(0.5))
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(1.0))
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(-1.0))
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(2.0))
    }

    @Test
    fun `presence wait is never used so idle always polls connect`() {
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 31,
                transportIsBle = true,
                hasDeviceAddress = true,
                presenceAvailable = true,
            ),
        )
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 34,
                transportIsBle = true,
                hasDeviceAddress = true,
                presenceAvailable = true,
            ),
        )
        assertFalse(
            IdleReconnectPolicy.shouldUsePresenceObservation(
                apiLevel = 30,
                transportIsBle = false,
                hasDeviceAddress = false,
                presenceAvailable = false,
            ),
        )
    }
}
