package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmSessionStallPolicyTest {

    @Test
    fun `streak below threshold keeps polling`() {
        assertFalse(ElmSessionStallPolicy.shouldRecoverSession(0))
        assertFalse(
            ElmSessionStallPolicy.shouldRecoverSession(
                ElmSessionStallPolicy.DEFAULT_TIMEOUT_STREAK - 1,
            ),
        )
    }

    @Test
    fun `streak at threshold recovers the session`() {
        assertTrue(
            ElmSessionStallPolicy.shouldRecoverSession(
                ElmSessionStallPolicy.DEFAULT_TIMEOUT_STREAK,
            ),
        )
        assertTrue(ElmSessionStallPolicy.shouldRecoverSession(415))
    }

    @Test
    fun `recovery does not depend on the VW UDS restore flag`() {
        // The old gate returned false for a healthy UDS state, which is every
        // non-MQB car — that is what let a mk4 TDI poll a dead link for 27 minutes.
        assertFalse(
            UdsRestorePolicy.shouldHardRecoverSession(
                udsRestoreUnhealthy = false,
                consecutiveEngineTimeouts = 415,
            ),
        )
        assertTrue(ElmSessionStallPolicy.shouldRecoverSession(415))
    }

    @Test
    fun `default streak recovers before the trip stop grace expires`() {
        val commandTimeoutMs = 4_000L
        val reinitMs = 10_000L
        val maxStreak = ElmSessionStallPolicy.maxStreakWithinGrace(commandTimeoutMs, reinitMs)

        assertTrue(
            "default streak must fire early enough to re-init inside the 90s grace",
            ElmSessionStallPolicy.DEFAULT_TIMEOUT_STREAK <= maxStreak,
        )

        val recoveredAtMs =
            ElmSessionStallPolicy.DEFAULT_TIMEOUT_STREAK * commandTimeoutMs + reinitMs
        assertFalse(
            "live PIDs must resume before EcuTrackingGate ends the trip",
            EcuTrackingGate.shouldStopForDisconnect(
                disconnectStartedAtMs = 0L,
                nowMs = recoveredAtMs,
            ),
        )
    }

    @Test
    fun `default streak re-inits a hung adapter within 30 seconds`() {
        assertTrue(ElmSessionStallPolicy.DEFAULT_TIMEOUT_STREAK * 4_000L <= 30_000L)
    }

    @Test
    fun `max streak within grace shrinks as re-init gets slower`() {
        assertEquals(20, ElmSessionStallPolicy.maxStreakWithinGrace(4_000L, 10_000L))
        assertEquals(15, ElmSessionStallPolicy.maxStreakWithinGrace(4_000L, 30_000L))
        assertEquals(0, ElmSessionStallPolicy.maxStreakWithinGrace(4_000L, 90_000L))
    }

    @Test
    fun `degenerate inputs never claim a recovery window`() {
        assertEquals(0, ElmSessionStallPolicy.maxStreakWithinGrace(0L, 10_000L))
        assertFalse(ElmSessionStallPolicy.shouldRecoverSession(415, streak = 0))
    }
}
