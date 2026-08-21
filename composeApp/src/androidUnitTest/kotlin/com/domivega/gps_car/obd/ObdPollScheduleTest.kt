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

    @Test
    fun `incomplete 0120 still schedules accelerator pedal`() {
        val firstPageOnly = setOf(
            0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D,
            0x0E, 0x0F, 0x10, 0x11, 0x13, 0x1C, 0x1F, 0x20,
        )
        assertEquals(
            listOf("0c", "0d", "49"),
            ObdPollSchedule.pidsForRound(
                round = 1,
                hot = listOf("0c", "0d", "0b", "49"),
                slow = emptyList(),
                slowEvery = 5,
                supportedMode01 = firstPageOnly,
            ),
        )
    }
}
