package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuTrackingGateTest {

    @Test
    fun `connected state never stops tracking`() {
        assertFalse(
            EcuTrackingGate.shouldStopForDisconnect(
                disconnectStartedAtMs = null,
                nowMs = 100_000L,
            ),
        )
    }

    @Test
    fun `brief disconnect within grace keeps trip alive`() {
        val start = 1_000L
        assertFalse(
            EcuTrackingGate.shouldStopForDisconnect(
                disconnectStartedAtMs = start,
                nowMs = start + 30_000L,
                graceMs = 90_000L,
            ),
        )
        assertFalse(
            EcuTrackingGate.shouldStopForDisconnect(
                disconnectStartedAtMs = start,
                nowMs = start + 89_999L,
                graceMs = 90_000L,
            ),
        )
    }

    @Test
    fun `sustained disconnect after grace stops trip`() {
        val start = 1_000L
        assertTrue(
            EcuTrackingGate.shouldStopForDisconnect(
                disconnectStartedAtMs = start,
                nowMs = start + 90_000L,
                graceMs = 90_000L,
            ),
        )
        assertTrue(
            EcuTrackingGate.shouldStopForDisconnect(
                disconnectStartedAtMs = start,
                nowMs = start + 120_000L,
                graceMs = 90_000L,
            ),
        )
    }

    @Test
    fun `ecu online ensures start and clears disconnect grace`() {
        val decision = EcuTrackingGate.evaluate(
            ecuConnected = true,
            isTracking = false,
            disconnectStartedAtMs = 50_000L,
            nowMs = 100_000L,
        )
        assertEquals(EcuTrackingAction.EnsureStart, decision.action)
        assertNull(decision.disconnectStartedAtMs)
    }

    @Test
    fun `ecu online while already tracking still ensures start`() {
        val decision = EcuTrackingGate.evaluate(
            ecuConnected = true,
            isTracking = true,
            disconnectStartedAtMs = null,
            nowMs = 100_000L,
        )
        assertEquals(EcuTrackingAction.EnsureStart, decision.action)
        assertNull(decision.disconnectStartedAtMs)
    }

    @Test
    fun `idle offline without a trip does nothing`() {
        val decision = EcuTrackingGate.evaluate(
            ecuConnected = false,
            isTracking = false,
            disconnectStartedAtMs = 12_000L,
            nowMs = 100_000L,
        )
        assertEquals(EcuTrackingAction.None, decision.action)
        assertNull(decision.disconnectStartedAtMs)
    }

    @Test
    fun `tracking while already offline arms grace without waiting for ecu edge`() {
        // Bug: manual/sticky start with ECU already false never saw a true→false edge.
        val now = 200_000L
        val decision = EcuTrackingGate.evaluate(
            ecuConnected = false,
            isTracking = true,
            disconnectStartedAtMs = null,
            nowMs = now,
        )
        assertEquals(EcuTrackingAction.None, decision.action)
        assertEquals(now, decision.disconnectStartedAtMs)
    }

    @Test
    fun `tracking offline past grace requests stop`() {
        val started = 10_000L
        val decision = EcuTrackingGate.evaluate(
            ecuConnected = false,
            isTracking = true,
            disconnectStartedAtMs = started,
            nowMs = started + 90_000L,
            graceMs = 90_000L,
        )
        assertEquals(EcuTrackingAction.EndTrip, decision.action)
        assertNull(decision.disconnectStartedAtMs)
    }

    @Test
    fun `tracking offline within grace keeps armed timer`() {
        val started = 10_000L
        val decision = EcuTrackingGate.evaluate(
            ecuConnected = false,
            isTracking = true,
            disconnectStartedAtMs = started,
            nowMs = started + 30_000L,
            graceMs = 90_000L,
        )
        assertEquals(EcuTrackingAction.None, decision.action)
        assertEquals(started, decision.disconnectStartedAtMs)
    }

    @Test
    fun `server bind requires live ECU flag`() {
        // Flag is live-PID-backed upstream (0c/0d/42); gate only checks the boolean.
        assertFalse(EcuTrackingGate.shouldBindServerSession(ecuConnected = false))
        assertTrue(EcuTrackingGate.shouldBindServerSession(ecuConnected = true))
    }
}
