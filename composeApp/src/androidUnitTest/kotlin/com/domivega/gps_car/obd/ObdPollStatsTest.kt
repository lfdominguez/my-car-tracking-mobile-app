package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdPollStatsTest {

    @Test
    fun tally_isEmptyWhenNothingMissed() {
        assertEquals(
            "",
            ObdPollStats.formatMissTally(ok = mapOf("0c" to 38, "0d" to 38), miss = emptyMap()),
        )
    }

    @Test
    fun tally_isEmptyWhenNothingWasAttempted() {
        assertEquals("", ObdPollStats.formatMissTally(ok = emptyMap(), miss = emptyMap()))
    }

    @Test
    fun tally_reportsOkOverAttemptsWorstFirst() {
        val out = ObdPollStats.formatMissTally(
            ok = mapOf("49" to 12, "2f" to 9, "46" to 1),
            miss = mapOf("49" to 2, "2f" to 3, "46" to 11),
        )
        // 46 misses 11/12, 2f 3/12, 49 2/14.
        assertEquals("worst=46:1/12 2f:9/12 49:12/14", out)
    }

    @Test
    fun tally_includesPidsThatOnlyEverMissed() {
        val out = ObdPollStats.formatMissTally(ok = emptyMap(), miss = mapOf("0f" to 7))
        assertEquals("worst=0f:0/7", out)
    }

    @Test
    fun tally_breaksRatioTiesByAttemptsSoTheLoudestPidWins() {
        val out = ObdPollStats.formatMissTally(
            ok = mapOf("05" to 1, "33" to 10),
            miss = mapOf("05" to 1, "33" to 10),
        )
        assertEquals("worst=33:10/20 05:1/2", out)
    }

    @Test
    fun tally_truncatesToTopN() {
        val ok = (0 until 12).associate { "%02x".format(it) to 1 }
        val miss = (0 until 12).associate { "%02x".format(it) to 1 }
        val out = ObdPollStats.formatMissTally(ok, miss, topN = 3)
        assertEquals(3, out.removePrefix("worst=").split(" ").size)
        assertTrue(out.startsWith("worst="))
    }

    @Test
    fun tally_topNBelowOneRendersNothing() {
        assertEquals(
            "",
            ObdPollStats.formatMissTally(ok = mapOf("0c" to 1), miss = mapOf("0c" to 1), topN = 0),
        )
    }
}
