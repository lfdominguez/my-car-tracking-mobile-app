package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdPollScheduleTest {
    private val hot = listOf("0c", "0d", "10")
    private val slow = listOf("2f", "05")

    @Test
    fun `round 1 is hot only when slowEvery is 5`() {
        // rounds are 1-based in API: slow when round % slowEvery == 0
        assertEquals(hot, ObdPollSchedule.pidsForRound(1, hot, slow, slowEvery = 5))
        assertEquals(hot, ObdPollSchedule.pidsForRound(2, hot, slow, slowEvery = 5))
        assertEquals(hot, ObdPollSchedule.pidsForRound(4, hot, slow, slowEvery = 5))
    }

    @Test
    fun `every 5th round appends slow after hot`() {
        assertEquals(hot + slow, ObdPollSchedule.pidsForRound(5, hot, slow, slowEvery = 5))
        assertEquals(hot + slow, ObdPollSchedule.pidsForRound(10, hot, slow, slowEvery = 5))
    }

    @Test
    fun `slowEvery 1 always includes slow`() {
        assertEquals(hot + slow, ObdPollSchedule.pidsForRound(1, hot, slow, slowEvery = 1))
    }
}
