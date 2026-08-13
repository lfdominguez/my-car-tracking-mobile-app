package com.domivega.gps_car.obd

/**
 * Idle reconnect: poll connect every minute. Do not wait for Companion / scan
 * "device found" callbacks — those can leave the adapter idle until the app is opened.
 */
object IdleReconnectPolicy {
    const val INTERVAL_MS: Long = 60_000L
    const val FALLBACK_MIN_MS: Long = INTERVAL_MS
    const val FALLBACK_MAX_MS: Long = INTERVAL_MS
    const val MIN_PRESENCE_API: Int = 31

    fun shouldUsePresenceObservation(
        @Suppress("UNUSED_PARAMETER") apiLevel: Int,
        @Suppress("UNUSED_PARAMETER") transportIsBle: Boolean,
        @Suppress("UNUSED_PARAMETER") hasDeviceAddress: Boolean,
        @Suppress("UNUSED_PARAMETER") presenceAvailable: Boolean,
    ): Boolean = false

    /** Idle gap between connect attempts. [random01] kept for call-site compatibility. */
    fun nextDelayMs(@Suppress("UNUSED_PARAMETER") random01: Double): Long = INTERVAL_MS
}
