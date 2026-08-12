package com.domivega.gps_car.data.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrackingIdsTest {

    @Test
    fun `wrapLocal prefixes uuid`() {
        assertEquals("local:abc-123", LocalTrackingIds.wrapLocal("abc-123"))
    }

    @Test
    fun `wrapLocal does not double prefix`() {
        assertEquals("local:abc", LocalTrackingIds.wrapLocal("local:abc"))
    }

    @Test
    fun `isLocal detects prefix`() {
        assertTrue(LocalTrackingIds.isLocal("local:x"))
        assertFalse(LocalTrackingIds.isLocal("550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `isUploadable rejects local and blank`() {
        assertFalse(LocalTrackingIds.isUploadable("local:abc"))
        assertFalse(LocalTrackingIds.isUploadable(""))
        assertFalse(LocalTrackingIds.isUploadable("   "))
        assertTrue(LocalTrackingIds.isUploadable("550e8400-e29b-41d4-a716-446655440000"))
    }
}
