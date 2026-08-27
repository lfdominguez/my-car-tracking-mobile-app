package com.domivega.gps_car.gps

/**
 * Decides whether the most recent cached GPS fix still describes where the car is
 * *now*.
 *
 * The sample clock runs at a fixed 1 Hz while the location provider delivers fixes
 * on its own schedule — or stops delivering them entirely in a tunnel, a garage, or
 * when the provider is switched off. A tick reuses the cached fix only while it is
 * recent and accurate enough; otherwise the sample is uploaded without coordinates
 * rather than pinning the car to a place it has already left.
 *
 * Age must be measured against a monotonic clock (`Location.elapsedRealtimeNanos`),
 * never wall time, so an NTP correction cannot make a stale fix look fresh.
 */
object GpsFixFreshness {
    /** Three ticks of slack: a 1 Hz provider may skip a fix without dropping the track. */
    const val DEFAULT_MAX_FIX_AGE_MS: Long = 3_000L

    fun isUsable(
        fixAgeMs: Long?,
        accuracyMeters: Double?,
        maxAccuracyMeters: Double,
        maxFixAgeMs: Long = DEFAULT_MAX_FIX_AGE_MS,
    ): Boolean {
        if (fixAgeMs == null || accuracyMeters == null) return false
        // A negative age means the fix claims to be from the future — treat as unusable.
        if (fixAgeMs < 0L || fixAgeMs > maxFixAgeMs) return false
        if (!accuracyMeters.isFinite() || accuracyMeters < 0.0) return false
        return accuracyMeters <= maxAccuracyMeters
    }
}
