package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmPerformanceModeTest {

    @Test
    fun adaptiveTiming_normalIsAtat1() {
        assertEquals("ATAT1", ElmPerformanceMode.adaptiveTimingCommand(performance = false))
    }

    @Test
    fun adaptiveTiming_performanceIsAtat2() {
        assertEquals("ATAT2", ElmPerformanceMode.adaptiveTimingCommand(performance = true))
    }

    @Test
    fun mode01Poll_normalHasNoLineSuffix() {
        assertEquals("010C", ElmPerformanceMode.mode01PollCommand("0c", performance = false))
        assertEquals("010D", ElmPerformanceMode.mode01PollCommand("0d", performance = false))
    }

    @Test
    fun mode01Poll_performanceAppendsOneLineForSingleFramePids() {
        assertEquals("010C1", ElmPerformanceMode.mode01PollCommand("0c", performance = true))
        assertEquals("010D1", ElmPerformanceMode.mode01PollCommand("0d", performance = true))
        assertEquals("01421", ElmPerformanceMode.mode01PollCommand("42", performance = true))
        assertEquals("01101", ElmPerformanceMode.mode01PollCommand("10", performance = true))
    }

    @Test
    fun mode01Poll_neverSuffixesOdometerA6OrSupportBitmaps() {
        assertEquals("01A6", ElmPerformanceMode.mode01PollCommand("a6", performance = true))
        assertEquals("0100", ElmPerformanceMode.mode01PollCommand("00", performance = true))
        assertEquals("0120", ElmPerformanceMode.mode01PollCommand("20", performance = true))
        assertEquals("0140", ElmPerformanceMode.mode01PollCommand("40", performance = true))
    }

    @Test
    fun expectsSingleResponseLine_falseForA6AndSupport() {
        assertTrue(ElmPerformanceMode.expectsSingleResponseLine("0c"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("a6"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("00"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("not-hex"))
    }
}
