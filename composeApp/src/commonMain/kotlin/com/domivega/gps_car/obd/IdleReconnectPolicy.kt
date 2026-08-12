package com.domivega.gps_car.obd

/**
 * Idle “waiting for car” reconnect policy: Companion presence when possible,
 * otherwise a slow jittered fallback delay (meets ~1 minute connect SLA).
 */
object IdleReconnectPolicy {
    const val FALLBACK_MIN_MS: Long = 30_000L
    const val FALLBACK_MAX_MS: Long = 45_000L
    const val MIN_PRESENCE_API: Int = 31

    fun shouldUsePresenceObservation(
        apiLevel: Int,
        transportIsBle: Boolean,
        hasDeviceAddress: Boolean,
        presenceAvailable: Boolean,
    ): Boolean =
        apiLevel >= MIN_PRESENCE_API &&
            transportIsBle &&
            hasDeviceAddress &&
            presenceAvailable

    /** Inclusive jitter between [FALLBACK_MIN_MS] and [FALLBACK_MAX_MS]. [random01] in 0.0..1.0 */
    fun nextDelayMs(random01: Double): Long {
        val t = random01.coerceIn(0.0, 1.0)
        return FALLBACK_MIN_MS + ((FALLBACK_MAX_MS - FALLBACK_MIN_MS) * t).toLong()
    }
}
