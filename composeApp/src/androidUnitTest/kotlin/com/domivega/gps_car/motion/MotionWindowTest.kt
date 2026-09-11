package com.domivega.gps_car.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionWindowTest {

    private fun upright() = doubleArrayOf(0.0, 0.0, 1.0)

    /** Feed [n] identical readings, the way a steady second of sensor data arrives. */
    private fun MotionWindow.feed(n: Int, value: Double, tilt: DoubleArray? = upright()) {
        repeat(n) { add(value, tilt) }
    }

    @Test
    fun `a sparse second is not characterised at all`() {
        val w = MotionWindow()
        w.feed(3, 4.0)
        // Fewer readings than MIN_SAMPLES: the backend must see "unknown", not a number
        // built from three data points.
        assertNull(w.drain())
    }

    @Test
    fun `a steady second reports peak and RMS close together`() {
        val w = MotionWindow()
        w.feed(50, 3.0)
        val a = w.drain()!!
        assertEquals(3.0, a.peakMps2, 1e-9)
        assertEquals(3.0, a.rmsMps2, 1e-9)
        assertEquals(50, a.sampleCount)
    }

    @Test
    fun `one spike among quiet readings leaves RMS far below peak`() {
        // This is what separates a pothole from braking; the backend rejects the window
        // when RMS falls too far under the peak.
        val w = MotionWindow()
        w.feed(49, 0.1)
        w.add(8.0, upright())
        val a = w.drain()!!
        assertEquals(8.0, a.peakMps2, 1e-9)
        assertTrue("RMS ${a.rmsMps2} should stay well under the peak", a.rmsMps2 < 2.0)
    }

    @Test
    fun `tilt delta accumulates against the start of the window`() {
        // A slow, steady swing must add up rather than read as many negligible steps.
        val w = MotionWindow()
        w.feed(10, 1.0, upright())
        w.add(1.0, doubleArrayOf(0.0, 1.0, 0.0)) // 90° over from upright
        val a = w.drain()!!
        assertEquals(90.0, a.tiltDeltaDeg!!, 1e-6)
    }

    @Test
    fun `a window without orientation still reports magnitudes`() {
        // Losing the rotation vector only costs the handling check, not the whole sample.
        val w = MotionWindow()
        w.feed(20, 2.0, tilt = null)
        val a = w.drain()!!
        assertEquals(2.0, a.peakMps2, 1e-9)
        assertNull(a.tiltDeltaDeg)
    }

    @Test
    fun `draining starts a fresh window`() {
        val w = MotionWindow()
        w.feed(20, 5.0)
        assertNotNull(w.drain())
        w.feed(20, 1.0)
        assertEquals(1.0, w.drain()!!.peakMps2, 1e-9)
    }

    @Test
    fun `reset discards a partial window`() {
        val w = MotionWindow()
        w.feed(20, 5.0)
        w.reset()
        assertNull(w.drain())
    }

    @Test
    fun `impossible readings are ignored`() {
        val w = MotionWindow()
        w.add(Double.NaN, upright())
        w.add(-1.0, upright())
        w.feed(10, 2.0)
        val a = w.drain()!!
        assertEquals(10, a.sampleCount)
        assertEquals(2.0, a.peakMps2, 1e-9)
    }
}
