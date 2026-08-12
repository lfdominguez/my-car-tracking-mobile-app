package com.domivega.gps_car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCollectionGateTest {

    @Test
    fun `collect allowed while tracking with matching epoch and session`() {
        assertTrue(
            TrackingCollectionGate.shouldCollect(
                epochAtStart = 3L,
                currentEpoch = 3L,
                isTracking = true,
                sessionId = "local:abc",
            ),
        )
    }

    @Test
    fun `collect rejected after stop clears tracking flag`() {
        assertFalse(
            TrackingCollectionGate.shouldCollect(
                epochAtStart = 3L,
                currentEpoch = 3L,
                isTracking = false,
                sessionId = "local:abc",
            ),
        )
    }

    @Test
    fun `collect rejected when stop bumps epoch even if session still visible`() {
        // Race: coroutine saw old epoch; Stop bumped generation before body ran.
        assertFalse(
            TrackingCollectionGate.shouldCollect(
                epochAtStart = 3L,
                currentEpoch = 4L,
                isTracking = true,
                sessionId = "local:abc",
            ),
        )
    }

    @Test
    fun `collect rejected without session id — must not create one on GPS path`() {
        assertFalse(
            TrackingCollectionGate.shouldCollect(
                epochAtStart = 1L,
                currentEpoch = 1L,
                isTracking = true,
                sessionId = null,
            ),
        )
        assertFalse(
            TrackingCollectionGate.shouldCollect(
                epochAtStart = 1L,
                currentEpoch = 1L,
                isTracking = true,
                sessionId = "  ",
            ),
        )
    }

    @Test
    fun `bind commit allowed only when still tracking same epoch and uploadable`() {
        assertTrue(
            TrackingCollectionGate.shouldCommitBoundSession(
                epochAtStart = 2L,
                currentEpoch = 2L,
                isTracking = true,
                serverId = "srv-1",
                isUploadable = { it == "srv-1" },
            ),
        )
    }

    @Test
    fun `bind commit rejected after stop during network start`() {
        assertFalse(
            TrackingCollectionGate.shouldCommitBoundSession(
                epochAtStart = 2L,
                currentEpoch = 3L,
                isTracking = false,
                serverId = "srv-1",
                isUploadable = { true },
            ),
        )
        assertFalse(
            TrackingCollectionGate.shouldCommitBoundSession(
                epochAtStart = 2L,
                currentEpoch = 3L,
                isTracking = true,
                serverId = "srv-1",
                isUploadable = { true },
            ),
        )
    }

    @Test
    fun `bind commit rejected for local or null server id`() {
        assertFalse(
            TrackingCollectionGate.shouldCommitBoundSession(
                epochAtStart = 1L,
                currentEpoch = 1L,
                isTracking = true,
                serverId = null,
                isUploadable = { true },
            ),
        )
        assertFalse(
            TrackingCollectionGate.shouldCommitBoundSession(
                epochAtStart = 1L,
                currentEpoch = 1L,
                isTracking = true,
                serverId = "local:x",
                isUploadable = { !it.startsWith("local:") },
            ),
        )
    }
}
