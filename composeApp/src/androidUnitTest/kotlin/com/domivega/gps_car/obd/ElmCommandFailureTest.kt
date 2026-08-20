package com.domivega.gps_car.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmCommandFailureTest {

    @Test
    fun `GATT write failure is a fatal link error`() {
        assertTrue(ElmCommandFailure.isFatalWrite("Write failed for 012F"))
        assertTrue(ElmCommandFailure.isFatalWrite("Write failed for 015E"))
    }

    @Test
    fun `command timeout is not a fatal write`() {
        assertFalse(ElmCommandFailure.isFatalWrite("Timeout waiting for response to 012F"))
        assertFalse(ElmCommandFailure.isFatalWrite(null))
        assertFalse(ElmCommandFailure.isFatalWrite("NO DATA"))
    }

    @Test
    fun `fatal write or unlinked transport aborts the poll round`() {
        assertTrue(
            ElmCommandFailure.shouldAbortPoll(
                isLinked = true,
                writeFailed = true,
            ),
        )
        assertTrue(
            ElmCommandFailure.shouldAbortPoll(
                isLinked = false,
                writeFailed = false,
            ),
        )
        assertFalse(
            ElmCommandFailure.shouldAbortPoll(
                isLinked = true,
                writeFailed = false,
            ),
        )
    }
}
