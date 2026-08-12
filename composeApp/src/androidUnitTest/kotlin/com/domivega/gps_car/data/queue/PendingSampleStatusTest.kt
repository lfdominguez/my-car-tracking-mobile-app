package com.domivega.gps_car.data.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingSampleStatusTest {

    @Test
    fun statusConstantsMatchDesign() {
        assertEquals("PENDING", PendingSampleStatus.PENDING)
        assertEquals("IN_FLIGHT", PendingSampleStatus.IN_FLIGHT)
        assertEquals("FAILED", PendingSampleStatus.FAILED)
        assertEquals("DEAD", PendingSampleStatus.DEAD)
        assertEquals(50, PendingSampleStatus.MAX_ATTEMPTS)
        assertTrue(PendingSampleStatus.ALL.containsAll(
            listOf(
                PendingSampleStatus.PENDING,
                PendingSampleStatus.IN_FLIGHT,
                PendingSampleStatus.FAILED,
                PendingSampleStatus.DEAD,
            )
        ))
    }

    @Test
    fun stuckForManualRequeueIncludesDeadFailedAndInFlightOnly() {
        assertTrue(PendingSampleStatus.isStuckForRequeue(PendingSampleStatus.DEAD))
        assertTrue(PendingSampleStatus.isStuckForRequeue(PendingSampleStatus.FAILED))
        assertTrue(PendingSampleStatus.isStuckForRequeue(PendingSampleStatus.IN_FLIGHT))
        assertTrue(!PendingSampleStatus.isStuckForRequeue(PendingSampleStatus.PENDING))
        assertEquals(
            setOf(
                PendingSampleStatus.DEAD,
                PendingSampleStatus.FAILED,
                PendingSampleStatus.IN_FLIGHT,
            ),
            PendingSampleStatus.STUCK_FOR_REQUEUE,
        )
    }
}
