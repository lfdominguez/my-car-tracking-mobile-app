package com.domivega.gps_car.data.queue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleRejectionTest {

    @Test
    fun `duplicate is terminal and duplicate`() {
        assertTrue(SampleRejection.isDuplicate("duplicate"))
        assertTrue(SampleRejection.isTerminal("duplicate"))
    }

    @Test
    fun `track_finished is not terminal so retry can drain after server fix`() {
        assertFalse(SampleRejection.isTerminal("track_finished"))
        assertFalse(SampleRejection.isDuplicate("track_finished"))
    }

    @Test
    fun `unknown track is terminal`() {
        assertTrue(SampleRejection.isTerminal("unknown_track"))
        assertTrue(SampleRejection.isTerminal("unknown tracking_id"))
    }

    @Test
    fun `invalid coords is terminal`() {
        assertTrue(SampleRejection.isTerminal("invalid_coords"))
        assertTrue(SampleRejection.isTerminal("invalid lat/lon"))
    }

    @Test
    fun `generic error is not terminal`() {
        assertFalse(SampleRejection.isTerminal("error"))
        assertFalse(SampleRejection.isTerminal("db_error"))
    }
}
