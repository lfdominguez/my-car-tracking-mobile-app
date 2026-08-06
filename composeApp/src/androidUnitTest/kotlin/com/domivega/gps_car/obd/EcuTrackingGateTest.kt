package com.domivega.gps_car.obd

import org.junit.Assert.assertFalse
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
}
