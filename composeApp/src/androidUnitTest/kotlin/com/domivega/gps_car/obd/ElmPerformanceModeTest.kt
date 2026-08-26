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
    fun mode01Poll_singleResponderAppendsOneLineWithoutPerformanceMode() {
        // Physical header (7E0): one module answers, so "stop after 1 line" cannot
        // drop a second ECU's reply. This is the default polling path now.
        assertEquals(
            "010C1",
            ElmPerformanceMode.mode01PollCommand("0c", performance = false, singleResponder = true),
        )
        assertEquals(
            "01491",
            ElmPerformanceMode.mode01PollCommand("49", performance = false, singleResponder = true),
        )
    }

    @Test
    fun mode01Poll_singleResponderStillSkipsMultiFrameAndBitmaps() {
        assertEquals(
            "01A6",
            ElmPerformanceMode.mode01PollCommand("a6", performance = false, singleResponder = true),
        )
        assertEquals(
            "019A",
            ElmPerformanceMode.mode01PollCommand("9a", performance = false, singleResponder = true),
        )
        assertEquals(
            "0100",
            ElmPerformanceMode.mode01PollCommand("00", performance = false, singleResponder = true),
        )
        assertEquals(
            "0140",
            ElmPerformanceMode.mode01PollCommand("40", performance = false, singleResponder = true),
        )
    }

    @Test
    fun mode01Poll_functionalHeaderKeepsNoSuffix() {
        assertEquals(
            "0105",
            ElmPerformanceMode.mode01PollCommand("05", performance = false, singleResponder = false),
        )
    }

    @Test
    fun responseTimeout_rendersAtstForEachCeiling() {
        assertEquals(
            "ATST32",
            ElmPerformanceMode.responseTimeoutCommand(ElmPerformanceMode.ST_BASELINE_HEX),
        )
        assertEquals(
            "ATST96",
            ElmPerformanceMode.responseTimeoutCommand(ElmPerformanceMode.ST_SINGLE_RESPONDER_HEX),
        )
        assertEquals(
            "ATST64",
            ElmPerformanceMode.responseTimeoutCommand(ElmPerformanceMode.ST_FUNCTIONAL_HEX),
        )
        assertEquals("ATST7F", ElmPerformanceMode.responseTimeoutCommand(" 7f "))
    }

    @Test
    fun expectsSingleResponseLine_falseForA6AndSupport() {
        assertTrue(ElmPerformanceMode.expectsSingleResponseLine("0c"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("a6"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("00"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("not-hex"))
    }
}
