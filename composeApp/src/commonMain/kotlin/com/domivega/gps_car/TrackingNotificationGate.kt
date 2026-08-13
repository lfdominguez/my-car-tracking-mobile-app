package com.domivega.gps_car

/**
 * Whether the foreground notification may still show live "Running v=…" text.
 *
 * Stop/shutdown must not be overwritten by a GPS coroutine that already passed
 * the collection gate, and shutdown flush must not keep the trip-looking banner.
 */
object TrackingNotificationGate {
    fun shouldPublishLiveTrackingStatus(
        isTracking: Boolean,
        shuttingDown: Boolean,
        epochAtStart: Long,
        currentEpoch: Long,
    ): Boolean {
        if (shuttingDown) return false
        if (!isTracking) return false
        if (epochAtStart != currentEpoch) return false
        return true
    }
}
