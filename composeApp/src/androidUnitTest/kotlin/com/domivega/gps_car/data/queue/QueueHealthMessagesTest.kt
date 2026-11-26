package com.domivega.gps_car.data.queue

import com.domivega.gps_car.ui.UiText
import com.domivega.gps_car.ui.argStrings
import com.domivega.gps_car.ui.key
import org.junit.Assert.assertEquals

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHealthMessagesTest {

    @Test
    fun `dead samples produce permanent warning`() {
        val msg = QueueHealthMessages.warning(failedCount = 0, deadCount = 2, lastFlushOk = true)
        assertNotNull(msg)
        assertEquals("msg_upload_dead", msg!!.key)
        assertEquals(2, (msg as UiText.Plural).count)
    }

    @Test
    fun `failed samples produce retry warning`() {
        val msg = QueueHealthMessages.warning(failedCount = 3, deadCount = 0, lastFlushOk = true)
        assertNotNull(msg)
        assertEquals("msg_upload_failing_count", msg!!.key)
        assertTrue(msg.argStrings.contains("3"))
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
        assertTrue(msg!!.argStrings.any { it.contains("track_finished") })
        assertTrue(msg.argStrings.contains("400"))
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

    @Test
    fun `pause messages explain the fix`() {
        assertNull(QueueHealthMessages.pauseMessage(null))
        val unlinked = QueueHealthMessages.pauseMessage(UploadPauseReason.DeviceUnauthorized)!!
        assertEquals("msg_upload_unlinked", unlinked.key)
    }
}
