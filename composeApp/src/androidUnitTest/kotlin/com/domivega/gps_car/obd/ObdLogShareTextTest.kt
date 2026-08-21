package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdLogShareTextTest {
    @Test
    fun `formats entries as utc time level initial and message`() {
        val text = formatObdLogAsText(
            listOf(
                ObdLogEntry(timestampMs = 3_661_000L, level = ObdLogLevel.INFO, message = "ELM init"),
                ObdLogEntry(timestampMs = 3_662_000L, level = ObdLogLevel.ERROR, message = "timeout"),
            ),
        )
        val lines = text.trimEnd().lines()
        assertEquals("GPS Car Tracking OBD debug log", lines.first())
        assertTrue(lines.contains("01:01:01 I ELM init"))
        assertTrue(lines.contains("01:01:02 E timeout"))
    }

    @Test
    fun `empty log still produces a header`() {
        val text = formatObdLogAsText(emptyList())
        assertTrue(text.startsWith("GPS Car Tracking OBD debug log"))
        assertTrue(text.contains("(no events)"))
    }
}
