package com.domivega.gps_car.motion

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MotionMathTest {

    /** Identity: device axes already aligned with the world (x East, y North, z Up). */
    private val identity = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    )

    /** Phone lying face-up but rotated 90° in the horizontal plane. */
    private val yawed90 = floatArrayOf(
        0f, -1f, 0f,
        1f, 0f, 0f,
        0f, 0f, 1f,
    )

    @Test
    fun `horizontal magnitude ignores the vertical component`() {
        // A pure vertical jolt — a pothole — is not driving behaviour.
        val m = MotionMath.horizontalMagnitude(identity, 0.0, 0.0, 9.0)
        assertEquals(0.0, m!!, 1e-9)
    }

    @Test
    fun `horizontal magnitude combines both horizontal axes`() {
        val m = MotionMath.horizontalMagnitude(identity, 3.0, 4.0, 0.0)
        assertEquals(5.0, m!!, 1e-9)
    }

    /** The whole point: the same real-world push reads the same however the phone sits. */
    @Test
    fun `horizontal magnitude does not depend on how the phone is turned`() {
        val straight = MotionMath.horizontalMagnitude(identity, 2.0, 1.0, 0.5)
        val turned = MotionMath.horizontalMagnitude(yawed90, 2.0, 1.0, 0.5)
        assertNotNull(straight)
        assertEquals(straight!!, turned!!, 1e-9)
        assertEquals(sqrt(5.0), straight, 1e-9)
    }

    @Test
    fun `a missing or malformed rotation matrix yields no reading`() {
        assertNull(MotionMath.horizontalMagnitude(null, 1.0, 1.0, 1.0))
        assertNull(MotionMath.horizontalMagnitude(floatArrayOf(1f, 0f), 1.0, 1.0, 1.0))
        assertNull(MotionMath.tiltVector(null))
    }

    @Test
    fun `a 4x4 rotation matrix is accepted`() {
        val m4 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        assertEquals(5.0, MotionMath.horizontalMagnitude(m4, 3.0, 4.0, 0.0)!!, 1e-9)
    }

    @Test
    fun `turning the phone about the vertical axis is not a tilt`() {
        // Cornering rotates the car — and the phone in its mount — about vertical. That
        // must not read as someone picking the phone up.
        val flat = MotionMath.tiltVector(identity)
        val alsoFlat = MotionMath.tiltVector(yawed90)
        assertEquals(0.0, MotionMath.angleBetweenDeg(flat, alsoFlat)!!, 1e-6)
    }

    @Test
    fun `tipping the phone on its side is a ninety degree tilt`() {
        val onItsSide = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        val angle = MotionMath.angleBetweenDeg(
            MotionMath.tiltVector(identity),
            MotionMath.tiltVector(onItsSide),
        )
        assertEquals(90.0, angle!!, 1e-6)
    }

    @Test
    fun `an angle needs two real directions`() {
        assertNull(MotionMath.angleBetweenDeg(null, doubleArrayOf(0.0, 0.0, 1.0)))
        assertNull(MotionMath.angleBetweenDeg(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0)))
    }
}
