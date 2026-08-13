package com.domivega.gps_car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingNotificationGateTest {

    @Test
    fun `live running status allowed only while tracking same epoch`() {
        assertTrue(
            TrackingNotificationGate.shouldPublishLiveTrackingStatus(
                isTracking = true,
                shuttingDown = false,
                epochAtStart = 4L,
                currentEpoch = 4L,
            ),
        )
    }

    @Test
    fun `live running status rejected after stop even if epoch still matches`() {
        assertFalse(
            TrackingNotificationGate.shouldPublishLiveTrackingStatus(
                isTracking = false,
                shuttingDown = false,
                epochAtStart = 4L,
                currentEpoch = 4L,
            ),
        )
    }

    @Test
    fun `live running status rejected when stop bumps epoch`() {
        assertFalse(
            TrackingNotificationGate.shouldPublishLiveTrackingStatus(
                isTracking = true,
                shuttingDown = false,
                epochAtStart = 4L,
                currentEpoch = 5L,
            ),
        )
    }

    @Test
    fun `live running status rejected during shutdown so leftover flush cannot keep Running text`() {
        assertFalse(
            TrackingNotificationGate.shouldPublishLiveTrackingStatus(
                isTracking = true,
                shuttingDown = true,
                epochAtStart = 4L,
                currentEpoch = 4L,
            ),
        )
    }
}
