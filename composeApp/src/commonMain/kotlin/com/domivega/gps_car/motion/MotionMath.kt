package com.domivega.gps_car.motion

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Pure geometry for turning a phone's linear-acceleration reading into something the
 * backend can compare across trips and across phones.
 *
 * The problem: `TYPE_LINEAR_ACCELERATION` is gravity-compensated but still expressed in
 * the *device's* axes, and a phone in a mount, a cupholder or a pocket points wherever
 * it happens to point — and can be re-oriented mid-trip. Raw x/y/z are therefore not
 * comparable to anything.
 *
 * The fix: rotate into the world frame with the platform's rotation matrix (x = East,
 * y = North, z = Up) and keep only the **horizontal magnitude**. That number does not
 * depend on which way the phone faces, and braking, accelerating and cornering all show
 * up in it. Which of those it was is decided on the server from the speed series — the
 * phone only reports how hard.
 */
object MotionMath {

    /**
     * Horizontal acceleration magnitude in m/s², given a device→world rotation matrix
     * (row-major 3x3 or 4x4, as Android's `getRotationMatrixFromVector` produces) and a
     * gravity-free device-frame reading.
     *
     * Returns null when the matrix is not a usable shape, so a caller with no
     * orientation yet simply records nothing rather than a meaningless number.
     */
    fun horizontalMagnitude(rotationMatrix: FloatArray?, x: Double, y: Double, z: Double): Double? {
        val stride = when (rotationMatrix?.size) {
            9 -> 3
            16 -> 4
            else -> return null
        }
        fun r(row: Int, col: Int) = rotationMatrix[row * stride + col].toDouble()
        // World east and north components; the up component is deliberately dropped —
        // road bumps are vertical and are not driving behaviour.
        val east = r(0, 0) * x + r(0, 1) * y + r(0, 2) * z
        val north = r(1, 0) * x + r(1, 1) * y + r(1, 2) * z
        val magnitude = sqrt(east * east + north * north)
        return magnitude.takeIf { it.isFinite() }
    }

    /**
     * The world's "up" direction expressed in device axes — i.e. which way the phone is
     * tilted — read out of the same rotation matrix. Comparing this vector over time is
     * how a handled phone is told apart from a braking car: handling swings it tens of
     * degrees, braking pitches it a few.
     *
     * Yaw is deliberately invisible here, so a car merely turning a corner does not look
     * like someone picking the phone up.
     */
    fun tiltVector(rotationMatrix: FloatArray?): DoubleArray? {
        val stride = when (rotationMatrix?.size) {
            9 -> 3
            16 -> 4
            else -> return null
        }
        val v = doubleArrayOf(
            rotationMatrix[2 * stride + 0].toDouble(),
            rotationMatrix[2 * stride + 1].toDouble(),
            rotationMatrix[2 * stride + 2].toDouble(),
        )
        return v.takeIf { it.all { c -> c.isFinite() } && length(it) > 0.0 }
    }

    /** Angle between two direction vectors, in degrees. Null if either has no length. */
    fun angleBetweenDeg(a: DoubleArray?, b: DoubleArray?): Double? {
        if (a == null || b == null || a.size < 3 || b.size < 3) return null
        val la = length(a)
        val lb = length(b)
        if (la <= 0.0 || lb <= 0.0) return null
        val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb)
        val clamped = dot.coerceIn(-1.0, 1.0)
        val deg = acos(clamped) * 180.0 / PI
        return deg.takeIf { it.isFinite() }
    }

    private const val PI = kotlin.math.PI

    private fun length(v: DoubleArray): Double = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
}
