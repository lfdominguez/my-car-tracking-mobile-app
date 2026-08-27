package com.domivega.gps_car.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRecordingGateTest {

    @Test
    fun `moving vehicle attaches the fresh fix`() {
        assertTrue(
            GpsRecordingGate.shouldAttachFreshFix(
                lastValidObdSpeedKph = 35.0,
                speedZeroSinceMs = null,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun `unknown speed is not parked`() {
        assertTrue(
            GpsRecordingGate.shouldAttachFreshFix(
                lastValidObdSpeedKph = null,
                speedZeroSinceMs = null,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun `zero speed within 10s still attaches the fresh fix`() {
        val since = 1_000L
        assertTrue(
            GpsRecordingGate.shouldAttachFreshFix(
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = since,
                nowMs = since + 10_000L,
            ),
        )
        assertTrue(
            GpsRecordingGate.shouldAttachFreshFix(
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = null,
                nowMs = since,
            ),
        )
    }

    /**
     * The sample itself still goes out — only the incoming fix is refused, so the
     * caller falls back to the frozen anchor and the track stops growing.
     */
    @Test
    fun `zero speed for more than 10s holds the anchor instead`() {
        val since = 1_000L
        assertFalse(
            GpsRecordingGate.shouldAttachFreshFix(
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = since,
                nowMs = since + 10_001L,
            ),
        )
    }

    @Test
    fun `zero hold disabled refuses a fresh fix immediately`() {
        assertFalse(
            GpsRecordingGate.shouldAttachFreshFix(
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = 1_000L,
                nowMs = 1_000L,
                parkedHoldMs = 0L,
            ),
        )
    }

    @Test
    fun `zero-since timer arms on first exact 0 speed and clears when moving`() {
        assertEquals(
            5_000L,
            GpsRecordingGate.nextZeroSinceMs(
                previousZeroSinceMs = null,
                lastValidObdSpeedKph = 0.0,
                nowMs = 5_000L,
            ),
        )
        assertEquals(
            5_000L,
            GpsRecordingGate.nextZeroSinceMs(
                previousZeroSinceMs = 5_000L,
                lastValidObdSpeedKph = 0.0,
                nowMs = 12_000L,
            ),
        )
        assertNull(
            GpsRecordingGate.nextZeroSinceMs(
                previousZeroSinceMs = 5_000L,
                lastValidObdSpeedKph = 12.0,
                nowMs = 13_000L,
            ),
        )
        assertNull(
            GpsRecordingGate.nextZeroSinceMs(
                previousZeroSinceMs = 5_000L,
                lastValidObdSpeedKph = null,
                nowMs = 13_000L,
            ),
        )
    }
}
