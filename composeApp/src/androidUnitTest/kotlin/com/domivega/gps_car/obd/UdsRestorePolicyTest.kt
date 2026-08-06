package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsRestorePolicyTest {

    @Test
    fun `single post-UDS health miss does not hard recover`() {
        assertFalse(
            UdsRestorePolicy.shouldHardRecoverSession(
                udsRestoreUnhealthy = true,
                consecutiveEngineTimeouts = 1,
            ),
        )
        assertFalse(
            UdsRestorePolicy.shouldHardRecoverSession(
                udsRestoreUnhealthy = true,
                consecutiveEngineTimeouts = 7,
            ),
        )
    }

    @Test
    fun `sustained timeouts after unhealthy restore allow hard recover`() {
        assertTrue(
            UdsRestorePolicy.shouldHardRecoverSession(
                udsRestoreUnhealthy = true,
                consecutiveEngineTimeouts = 8,
            ),
        )
    }

    @Test
    fun `healthy restore never hard recovers from timeouts alone via this gate`() {
        assertFalse(
            UdsRestorePolicy.shouldHardRecoverSession(
                udsRestoreUnhealthy = false,
                consecutiveEngineTimeouts = 100,
            ),
        )
    }

    @Test
    fun `failed health schedules UDS backoff`() {
        assertEquals(0L, UdsRestorePolicy.nextUdsAllowedAtMs(nowMs = 1000L, restoreHealthOk = true))
        assertEquals(
            1000L + UdsRestorePolicy.DEFAULT_BACKOFF_MS,
            UdsRestorePolicy.nextUdsAllowedAtMs(nowMs = 1000L, restoreHealthOk = false),
        )
    }
}
