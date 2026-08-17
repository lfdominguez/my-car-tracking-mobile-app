package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `parked sleep blocks idle connect until backoff elapses`() {
        val now = 1_000_000L
        val until = IdleReconnectPolicy.parkedSleepUntilMs(now)
        assertEquals(now + IdleReconnectPolicy.PARKED_BACKOFF_MS, until)
        assertFalse(IdleReconnectPolicy.shouldAttemptConnect(until, now))
        assertFalse(IdleReconnectPolicy.shouldAttemptConnect(until, until - 1L))
        assertTrue(IdleReconnectPolicy.shouldAttemptConnect(until, until))
        assertTrue(IdleReconnectPolicy.shouldAttemptConnect(null, now))
    }

    @Test
    fun `nextDelayMs waits out remaining parked sleep`() {
        val now = 50_000L
        val until = now + 180_000L
        assertEquals(180_000L, IdleReconnectPolicy.nextDelayMs(0.5, until, now))
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(0.5, until, until))
        assertEquals(60_000L, IdleReconnectPolicy.nextDelayMs(0.5, null, now))
    }
}
