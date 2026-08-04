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
}
