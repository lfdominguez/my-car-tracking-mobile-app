package com.domivega.gps_car.data.queue

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHealthMessagesTest {

    @Test
    fun `dead samples produce permanent warning`() {
        val msg = QueueHealthMessages.warning(failedCount = 0, deadCount = 2, lastFlushOk = true)
        assertNotNull(msg)
        assertTrue(msg!!.contains("2"))
        assertTrue(msg.contains("permanent", ignoreCase = true) || msg.contains("dead", ignoreCase = true))
    }

    @Test
    fun `failed samples produce retry warning`() {
        val msg = QueueHealthMessages.warning(failedCount = 3, deadCount = 0, lastFlushOk = true)
        assertNotNull(msg)
        assertTrue(msg!!.contains("retry", ignoreCase = true) || msg.contains("fail", ignoreCase = true))
    }

    @Test
    fun `last error is included in warning`() {
        val msg = QueueHealthMessages.warning(
            failedCount = 400,
            deadCount = 0,
            lastFlushOk = false,
            lastError = "track_finished",
        )
        assertNotNull(msg)
        assertTrue(msg!!.contains("track_finished"))
        assertTrue(msg.contains("400"))
    }

    @Test
    fun `last flush failure alone produces warning`() {
        val msg = QueueHealthMessages.warning(failedCount = 0, deadCount = 0, lastFlushOk = false)
        assertNotNull(msg)
    }

    @Test
    fun `healthy queue has no warning`() {
        assertNull(QueueHealthMessages.warning(failedCount = 0, deadCount = 0, lastFlushOk = true))
        assertNull(QueueHealthMessages.warning(failedCount = 0, deadCount = 0, lastFlushOk = null))
    }
}
