package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOdometerTrackerTest {

    @Test
    fun `currentKm null when not locked`() {
        val t = SessionOdometerTracker()
        assertFalse(t.isLocked)
        assertNull(t.currentKm(1000.0))
        assertNull(t.currentKm(null))
    }

    @Test
    fun `lock with pid31 returns baseline at start and advances with delta`() {
        val t = SessionOdometerTracker()
        t.lockBaseline(baselineKm = 45_000.0, pid31Km = 1_000.0)
        assertTrue(t.isLocked)
        assertEquals(45_000.0, t.currentKm(1_000.0)!!, 1e-9)
        assertEquals(45_005.0, t.currentKm(1_005.0)!!, 1e-9)
    }

    @Test
    fun `lock without pid31 holds baseline until first 31 then advances`() {
        val t = SessionOdometerTracker()
        t.lockBaseline(baselineKm = 45_000.0, pid31Km = null)
        assertEquals(45_000.0, t.currentKm(null)!!, 1e-9)
        assertEquals(45_000.0, t.currentKm(200.0)!!, 1e-9)
        assertEquals(45_010.0, t.currentKm(210.0)!!, 1e-9)
    }

    @Test
    fun `pid31 decrease does not move odometer backwards`() {
        val t = SessionOdometerTracker()
        t.lockBaseline(45_000.0, pid31Km = 1_000.0)
        assertEquals(45_010.0, t.currentKm(1_010.0)!!, 1e-9)
        assertEquals(45_000.0, t.currentKm(900.0)!!, 1e-9)
    }

    @Test
    fun `second lockBaseline ignored while locked`() {
        val t = SessionOdometerTracker()
        t.lockBaseline(45_000.0, pid31Km = 1_000.0)
        t.lockBaseline(99_000.0, pid31Km = 0.0)
        assertEquals(45_005.0, t.currentKm(1_005.0)!!, 1e-9)
    }

    @Test
    fun `reset clears lock`() {
        val t = SessionOdometerTracker()
        t.lockBaseline(45_000.0, pid31Km = 1_000.0)
        t.reset()
        assertFalse(t.isLocked)
        assertNull(t.currentKm(1_050.0))
        t.lockBaseline(10.0, pid31Km = 5.0)
        assertEquals(12.0, t.currentKm(7.0)!!, 1e-9)
    }
}
