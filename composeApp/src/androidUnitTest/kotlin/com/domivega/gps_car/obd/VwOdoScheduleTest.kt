package com.domivega.gps_car.obd

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VwOdoScheduleTest {

    private fun sched(
        engineOkMin: Int = 8,
        stopDwellMs: Long = 5_000L,
        stopSpeedMax: Double = 0.5,
    ) = VwOdoSchedule(
        engineOkMin = engineOkMin,
        stopDwellMs = stopDwellMs,
        stopSpeedMaxKmh = stopSpeedMax,
    )

    @Test
    fun `initial hop after engine OK when backoff clear`() {
        val s = sched(engineOkMin = 3)
        assertFalse(
            s.onPollTick(nowMs = 1_000, speedKmh = 10.0, engineOkCount = 2, udsNotBeforeMs = 0L).shouldHop,
        )
        val r = s.onPollTick(nowMs = 1_100, speedKmh = 10.0, engineOkCount = 3, udsNotBeforeMs = 0L)
        assertTrue(r.shouldHop)
        assertTrue(r.isInitial)
        s.onHopFinished(success = true)
        assertFalse(
            s.onPollTick(nowMs = 2_000, speedKmh = 10.0, engineOkCount = 20, udsNotBeforeMs = 0L).shouldHop,
        )
    }

    @Test
    fun `no hop while moving after initial`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, 30.0, 5, 0L).shouldHop)
        s.onHopFinished(false)
        assertFalse(s.onPollTick(10_000, 30.0, 50, 0L).shouldHop)
        assertFalse(s.onPollTick(60_000, 40.0, 50, 0L).shouldHop)
    }

    @Test
    fun `stop hop after 5s dwell once per stop`() {
        val s = sched(engineOkMin = 1)
        s.onPollTick(1_000, 20.0, 5, 0L).also { assertTrue(it.shouldHop) }
        s.onHopFinished(true)

        assertFalse(s.onPollTick(20_000, 0.0, 50, 0L).shouldHop)
        assertFalse(s.onPollTick(24_000, 0.0, 50, 0L).shouldHop)
        val stop = s.onPollTick(25_100, 0.0, 50, 0L)
        assertTrue(stop.shouldHop)
        assertFalse(stop.isInitial)
        s.onHopFinished(true)

        assertFalse(s.onPollTick(40_000, 0.0, 50, 0L).shouldHop)

        assertFalse(s.onPollTick(50_000, 15.0, 50, 0L).shouldHop)
        assertFalse(s.onPollTick(60_000, 0.0, 50, 0L).shouldHop)
        assertTrue(s.onPollTick(65_100, 0.0, 50, 0L).shouldHop)
    }

    @Test
    fun `initial while stopped consumes that stop`() {
        val s = sched(engineOkMin = 1)
        val r = s.onPollTick(1_000, 0.0, 5, 0L)
        assertTrue(r.shouldHop && r.isInitial)
        s.onHopFinished(true)
        assertFalse(s.onPollTick(20_000, 0.0, 50, 0L).shouldHop)
    }

    @Test
    fun `failed stop hop still consumes stop until move`() {
        val s = sched(engineOkMin = 1)
        s.onPollTick(1_000, 10.0, 5, 0L)
        s.onHopFinished(true)
        s.onPollTick(10_000, 0.0, 50, 0L)
        assertTrue(s.onPollTick(15_100, 0.0, 50, 0L).shouldHop)
        s.onHopFinished(success = false)
        assertFalse(s.onPollTick(20_000, 0.0, 50, 0L).shouldHop)
    }

    @Test
    fun `backoff suppresses hops`() {
        val s = sched(engineOkMin = 1)
        assertFalse(
            s.onPollTick(nowMs = 5_000, speedKmh = 0.0, engineOkCount = 10, udsNotBeforeMs = 10_000L).shouldHop,
        )
        assertTrue(
            s.onPollTick(nowMs = 10_000, speedKmh = 0.0, engineOkCount = 10, udsNotBeforeMs = 10_000L).shouldHop,
        )
    }

    @Test
    fun `null speed allows initial only not stop dwell`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, null, 5, 0L).shouldHop)
        s.onHopFinished(true)
        assertFalse(s.onPollTick(20_000, null, 50, 0L).shouldHop)
    }

    @Test
    fun `reset clears initial and stop state`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, 0.0, 5, 0L).shouldHop)
        s.onHopFinished(true)
        s.reset()
        assertTrue(s.onPollTick(2_000, 0.0, 5, 0L).shouldHop)
    }

    @Test
    fun `markLocked ends the initial chase but keeps once-per-stop hops`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, 10.0, 5, 0L).shouldHop)
        s.onHopFinished(true)
        s.markLocked()
        assertTrue(s.isLocked)

        // Still moving: no hop.
        assertFalse(s.onPollTick(20_000, 25.0, 50, 0L).shouldHop)
        // Stopped, but not yet through the dwell.
        assertFalse(s.onPollTick(30_000, 0.0, 50, 0L).shouldHop)
        // Dwell satisfied: hop, so cluster extras (fuel/oil/doors) refresh.
        assertTrue(s.onPollTick(35_100, 0.0, 50, 0L).shouldHop)
        s.onHopFinished(true)
        // That stop is consumed — no second hop without moving again.
        assertFalse(s.onPollTick(45_000, 0.0, 50, 0L).shouldHop)
    }

    @Test
    fun `locked schedule hops again at the next stop`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, 10.0, 5, 0L).shouldHop)
        s.onHopFinished(true)
        s.markLocked()

        // Dwell has to start before it can elapse.
        assertFalse(s.onPollTick(5_000, 0.0, 50, 0L).shouldHop)
        assertTrue(s.onPollTick(10_100, 0.0, 50, 0L).shouldHop)
        s.onHopFinished(true)
        assertFalse(s.onPollTick(15_000, 0.0, 50, 0L).shouldHop)

        // Drive off, stop again: another refresh is allowed.
        assertFalse(s.onPollTick(20_000, 30.0, 50, 0L).shouldHop)
        assertFalse(s.onPollTick(25_000, 0.0, 50, 0L).shouldHop)
        assertTrue(s.onPollTick(30_100, 0.0, 50, 0L).shouldHop)
    }

    @Test
    fun `uds backoff still suppresses hops while locked`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, 10.0, 5, 0L).shouldHop)
        s.onHopFinished(true)
        s.markLocked()

        assertFalse(s.onPollTick(10_100, 0.0, 50, udsNotBeforeMs = 60_000L).shouldHop)
        assertTrue(s.onPollTick(70_000, 0.0, 50, udsNotBeforeMs = 60_000L).shouldHop)
    }

    @Test
    fun `without markLocked stop hop still allowed after success`() {
        val s = sched(engineOkMin = 1)
        assertTrue(s.onPollTick(1_000, 20.0, 5, 0L).shouldHop)
        s.onHopFinished(true)
        // No markLocked — legacy stop schedule still applies
        assertFalse(s.onPollTick(20_000, 0.0, 50, 0L).shouldHop)
        assertTrue(s.onPollTick(25_100, 0.0, 50, 0L).shouldHop)
    }
}
