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

    @Test
    fun `skips Mode 01 pids the ECU did not advertise`() {
        val supported = setOf(0x0C, 0x0D, 0x10)
        assertEquals(
            listOf("0c", "0d", "10"),
            ObdPollSchedule.pidsForRound(
                round = 1,
                hot = listOf("0c", "0d", "42", "10"),
                slow = emptyList(),
                slowEvery = 5,
                supportedMode01 = supported,
            ),
        )
    }

    @Test
    fun `empty support bitmap still polls the full schedule`() {
        assertEquals(
            hot,
            ObdPollSchedule.pidsForRound(
                round = 1,
                hot = hot,
                slow = slow,
                slowEvery = 5,
                supportedMode01 = emptySet(),
            ),
        )
    }
}
