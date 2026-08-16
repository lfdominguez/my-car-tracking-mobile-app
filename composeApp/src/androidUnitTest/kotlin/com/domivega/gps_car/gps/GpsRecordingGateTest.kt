package com.domivega.gps_car.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRecordingGateTest {

    @Test
    fun `disconnected OBD never enqueues GPS`() {
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = false,
                vehicleOn = true,
                lastValidObdSpeedKph = 48.0,
                speedZeroSinceMs = null,
                nowMs = 20_000L,
            ),
        )
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = false,
                vehicleOn = false,
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = 1_000L,
                nowMs = 2_000L,
            ),
        )
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = false,
                vehicleOn = true,
                lastValidObdSpeedKph = null,
                speedZeroSinceMs = null,
                nowMs = 2_000L,
            ),
        )
    }

    @Test
    fun `vehicle off during 90s trip grace never enqueues GPS`() {
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = false,
                lastValidObdSpeedKph = 48.0,
                speedZeroSinceMs = null,
                nowMs = 20_000L,
            ),
        )
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = false,
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = 1_000L,
                nowMs = 2_000L,
            ),
        )
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = false,
                lastValidObdSpeedKph = null,
                speedZeroSinceMs = null,
                nowMs = 45_000L,
            ),
        )
    }

    @Test
    fun `connected moving vehicle enqueues GPS`() {
        assertTrue(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = true,
                lastValidObdSpeedKph = 35.0,
                speedZeroSinceMs = null,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun `connected with unknown speed still enqueues`() {
        assertTrue(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = true,
                lastValidObdSpeedKph = null,
                speedZeroSinceMs = null,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun `zero speed within 10s still enqueues`() {
        val since = 1_000L
        assertTrue(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = true,
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = since,
                nowMs = since + 10_000L,
            ),
        )
        assertTrue(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = true,
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = null,
                nowMs = since,
            ),
        )
    }

    @Test
    fun `zero speed for more than 10s drops GPS`() {
        val since = 1_000L
        assertFalse(
            GpsRecordingGate.shouldEnqueue(
                ecuConnected = true,
                vehicleOn = true,
                lastValidObdSpeedKph = 0.0,
                speedZeroSinceMs = since,
                nowMs = since + 10_001L,
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
