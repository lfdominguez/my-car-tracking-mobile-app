package com.domivega.gps_car.motion

import kotlin.math.sqrt

/** What the accelerometer saw during one 1 Hz sample. All magnitudes in m/s². */
data class MotionAggregate(
    /** Largest horizontal acceleration during the window. */
    val peakMps2: Double,
    /** RMS over the window. Stays near the peak for a sustained manoeuvre. */
    val rmsMps2: Double,
    /** Largest tilt change during the window, degrees; null if orientation was absent. */
    val tiltDeltaDeg: Double?,
    /** How many sensor readings went into the above. */
    val sampleCount: Int,
)

/**
 * Folds a ~50 Hz accelerometer stream into one aggregate per 1 Hz telemetry sample.
 *
 * Uploading the raw stream is not an option: it would be fifty times the queue volume
 * and the upload batches for no extra insight, since the backend only ever asks "how
 * hard was this second". Peak answers that. RMS is carried alongside it because peak
 * alone cannot tell a four-second brake from a single pothole — a sustained manoeuvre
 * keeps RMS near its peak, a jolt does not.
 *
 * Not thread-safe by design: it is fed from the sensor callback thread and drained from
 * the sample clock, so the caller owns the lock (see `ForegroundTrackingService`).
 */
class MotionWindow(
    /** Below this many readings a second is too sparse to characterise. */
    private val minSamples: Int = MIN_SAMPLES,
) {
    private var count = 0
    private var peak = 0.0
    private var sumSquares = 0.0
    private var firstTilt: DoubleArray? = null
    private var maxTiltDeltaDeg: Double? = null

    /**
     * Record one reading. [horizontalMps2] is already world-frame horizontal (see
     * [MotionMath.horizontalMagnitude]); [tilt] is optional and only feeds the
     * phone-handling check.
     */
    fun add(horizontalMps2: Double, tilt: DoubleArray? = null) {
        if (!horizontalMps2.isFinite() || horizontalMps2 < 0.0) return
        count += 1
        if (horizontalMps2 > peak) peak = horizontalMps2
        sumSquares += horizontalMps2 * horizontalMps2

        if (tilt == null) return
        val base = firstTilt
        if (base == null) {
            firstTilt = tilt.copyOf()
            return
        }
        // Measure against the window's first orientation rather than the previous
        // reading: a slow, steady swing accumulates instead of reading as many tiny
        // steps that each look like nothing.
        val delta = MotionMath.angleBetweenDeg(base, tilt) ?: return
        val current = maxTiltDeltaDeg
        if (current == null || delta > current) maxTiltDeltaDeg = delta
    }

    /**
     * Take the window's aggregate and start a new one. Returns null when the window was
     * too sparse to mean anything, in which case the sample carries no motion at all —
     * the backend treats that as unknown, not as zero.
     */
    fun drain(): MotionAggregate? {
        val aggregate = if (count >= minSamples) {
            MotionAggregate(
                peakMps2 = peak,
                rmsMps2 = sqrt(sumSquares / count),
                tiltDeltaDeg = maxTiltDeltaDeg,
                sampleCount = count,
            )
        } else {
            null
        }
        reset()
        return aggregate
    }

    /** Drop everything, e.g. when tracking stops or a new trip starts. */
    fun reset() {
        count = 0
        peak = 0.0
        sumSquares = 0.0
        firstTilt = null
        maxTiltDeltaDeg = null
    }

    companion object {
        /**
         * `SENSOR_DELAY_GAME` is ~50 Hz, but the platform batches and throttles freely
         * (doze, other apps, cheap sensors). Ten readings in a second is still enough to
         * separate a sustained manoeuvre from a spike; fewer is not.
         */
        const val MIN_SAMPLES = 10
    }
}
