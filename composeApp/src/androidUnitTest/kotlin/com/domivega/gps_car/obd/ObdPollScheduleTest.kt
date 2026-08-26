package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdPollScheduleTest {
    private val hot = listOf("0c", "0d", "10")
    private val slow = listOf("2f", "05")

    @Test
    fun `every round includes hot plus a slow slice, never hot alone`() {
        // Bursting all slow PIDs onto one round (old behavior) let a just-refreshed
        // slow value (e.g. fuel level) go stale for `slowEvery` rounds and then
        // expire before the burst round's response landed. Every round must carry
        // hot plus at least a slice of slow so no slow PID goes that long unrefreshed.
        for (round in 1..8) {
            val pids = ObdPollSchedule.pidsForRound(round, hot, slow, slowEvery = 5)
            assertEquals(hot, pids.take(hot.size))
            assertEquals(1, pids.size - hot.size)
        }
    }

    @Test
    fun `slow PIDs rotate and each one is covered within slowEvery rounds`() {
        val bigSlow = listOf("2f", "05", "46", "33")
        val seenByRound4 = (1..2).flatMap {
            ObdPollSchedule.pidsForRound(it, hot, bigSlow, slowEvery = 2).drop(hot.size)
        }.toSet()
        assertEquals(bigSlow.toSet(), seenByRound4)
    }

    @Test
    fun `slowEvery 1 always includes the full slow list every round`() {
        assertEquals(hot + slow, ObdPollSchedule.pidsForRound(1, hot, slow, slowEvery = 1))
        assertEquals(hot + slow, ObdPollSchedule.pidsForRound(2, hot, slow, slowEvery = 1))
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
                slow = emptyList(),
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

    @Test
    fun `session-disabled PIDs are dropped even without a support bitmap`() {
        // Failed 0100 leaves supportedMode01 empty, so this is the only filter
        // stopping dead PIDs from burning a timeout every rotation.
        assertEquals(
            listOf("0c", "0d"),
            ObdPollSchedule.pidsForRound(
                round = 1,
                hot = listOf("0c", "0d", "0b"),
                slow = listOf("a6"),
                slowEvery = 5,
                supportedMode01 = emptySet(),
                sessionDisabled = setOf("0b", "a6"),
            ),
        )
    }

    @Test
    fun `session-disabled matching is case insensitive`() {
        assertEquals(
            listOf("0c"),
            ObdPollSchedule.pidsForRound(
                round = 1,
                hot = listOf("0c", "A6"),
                slow = emptyList(),
                slowEvery = 5,
                sessionDisabled = setOf("a6"),
            ),
        )
    }

    // --- filter-before-slice -------------------------------------------------

    @Test
    fun unsupportedSlowPidsDoNotBurnRotationSlots() {
        // 0100 bitmap advertises 04/05/06/07 but not 5e/5b/9a. Filtering after the
        // slice spent 3 of every 17 slots on PIDs the ECU never answers.
        val slow = listOf("1f", "04", "5e", "5b", "9a", "05", "06", "07")
        val supported = setOf(0x0C, 0x1F, 0x04, 0x05, 0x06, 0x07)
        val seen = mutableSetOf<String>()
        repeat(20) { i ->
            val pids = ObdPollSchedule.pidsForRound(
                round = i + 1,
                hot = listOf("0c"),
                slow = slow,
                slowEvery = 5,
                supportedMode01 = supported,
            )
            val slowPart = pids.drop(1)
            // Every slot carries a PID that can actually answer.
            assertEquals(slowPart.size, slowPart.count { it in setOf("1f", "04", "05", "06", "07") })
            seen += slowPart
        }
        assertEquals(setOf("1f", "04", "05", "06", "07"), seen)
    }

    // --- urgency promotion ---------------------------------------------------

    @Test
    fun withoutAgesTheRotationIsUnchanged() {
        val slow = listOf("1f", "04", "43", "49", "44", "06", "07")
        repeat(15) { i ->
            val plain = ObdPollSchedule.pidsForRound(i + 1, listOf("0c"), slow)
            val withEmptyAges = ObdPollSchedule.pidsForRound(
                round = i + 1,
                hot = listOf("0c"),
                slow = slow,
                lastSeenAtMs = emptyMap(),
                nowMs = 1_000_000L,
            )
            assertEquals(plain, withEmptyAges)
        }
    }

    @Test
    fun pidPastHalfItsBudgetIsPromotedAheadOfTheRotation() {
        val slow = listOf("1f", "04", "43", "49", "44", "06", "07")
        val now = 1_000_000L
        // 49 last decoded 20 s ago: past half of the 30 s budget, and round 1's
        // rotation slice does not include it.
        val ages = slow.associateWith { now - 1_000L } + mapOf("49" to now - 20_000L)
        val pids = ObdPollSchedule.pidsForRound(
            round = 1,
            hot = listOf("0c"),
            slow = slow,
            lastSeenAtMs = ages,
            nowMs = now,
            slowBudgetMs = 30_000L,
        )
        assertEquals("0c", pids.first())
        assertEquals("49", pids[1])
    }

    @Test
    fun freshPidsAreNeverPromotedSoSteadyStateCostsNothing() {
        val slow = listOf("1f", "04", "43", "49", "44", "06", "07")
        val now = 1_000_000L
        val ages = slow.associateWith { now - 5_000L }
        val promoted = ObdPollSchedule.pidsForRound(
            round = 3,
            hot = listOf("0c"),
            slow = slow,
            lastSeenAtMs = ages,
            nowMs = now,
            slowBudgetMs = 30_000L,
        )
        assertEquals(ObdPollSchedule.pidsForRound(3, listOf("0c"), slow), promoted)
    }

    @Test
    fun neverDecodedPidsAreNotPromoted() {
        // A PID absent from lastSeenAtMs has no age to race; promoting it would
        // starve the ones actually about to expire.
        val slow = listOf("1f", "04", "43", "49", "44", "06", "07")
        val now = 1_000_000L
        val pids = ObdPollSchedule.pidsForRound(
            round = 1,
            hot = listOf("0c"),
            slow = slow,
            lastSeenAtMs = mapOf("1f" to now - 1_000L),
            nowMs = now,
            slowBudgetMs = 30_000L,
        )
        assertEquals(ObdPollSchedule.pidsForRound(1, listOf("0c"), slow), pids)
    }

    @Test
    fun promotionIsCappedSoARoundCannotBurst() {
        val slow = (0 until 14).map { "%02x".format(0x30 + it) }
        val now = 1_000_000L
        // Everything is stale at once — the worst case this cap exists for.
        val ages = slow.associateWith { now - 29_000L }
        val pids = ObdPollSchedule.pidsForRound(
            round = 1,
            hot = listOf("0c"),
            slow = slow,
            lastSeenAtMs = ages,
            nowMs = now,
            slowBudgetMs = 30_000L,
            maxSlowPerRound = 6,
        )
        assertEquals(1 + 6, pids.size)
    }

    @Test
    fun mostStalePidIsPromotedFirst() {
        val slow = listOf("1f", "04", "43", "49", "44", "06", "07")
        val now = 1_000_000L
        val ages = mapOf(
            "1f" to now - 1_000L,
            "04" to now - 1_000L,
            "43" to now - 1_000L,
            "49" to now - 18_000L,
            "44" to now - 1_000L,
            "06" to now - 26_000L,
            "07" to now - 1_000L,
        )
        val pids = ObdPollSchedule.pidsForRound(
            round = 1,
            hot = listOf("0c"),
            slow = slow,
            lastSeenAtMs = ages,
            nowMs = now,
            slowBudgetMs = 30_000L,
        )
        assertEquals(listOf("06", "49"), pids.drop(1).take(2))
    }
}
