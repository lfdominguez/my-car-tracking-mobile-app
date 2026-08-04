package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdLogBufferTest {
    @Test
    fun `appends newest at end and respects capacity`() {
        val buf = ObdLogBuffer(capacity = 3)
        buf.append(ObdLogLevel.INFO, "a", nowMs = 1)
        buf.append(ObdLogLevel.WARN, "b", nowMs = 2)
        buf.append(ObdLogLevel.ERROR, "c", nowMs = 3)
        buf.append(ObdLogLevel.INFO, "d", nowMs = 4)

        val snap = buf.snapshot()
        assertEquals(3, snap.size)
        assertEquals(listOf("b", "c", "d"), snap.map { it.message })
        assertEquals(ObdLogLevel.WARN, snap[0].level)
        assertEquals(4L, snap.last().timestampMs)
    }

    @Test
    fun `clear empties buffer`() {
        val buf = ObdLogBuffer(capacity = 10)
        buf.append(ObdLogLevel.INFO, "x", nowMs = 1)
        buf.clear()
        assertTrue(buf.snapshot().isEmpty())
    }
}
