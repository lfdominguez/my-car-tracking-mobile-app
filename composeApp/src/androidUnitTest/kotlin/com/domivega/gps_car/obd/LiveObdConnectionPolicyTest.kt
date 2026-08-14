package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveObdConnectionPolicyTest {

    @Test
    fun `live pid success marks connected and clears miss streak`() {
        val next = LiveObdConnectionPolicy.onLiveSuccess(
            currentlyConnected = false,
            consecutiveMisses = 3,
        )
        assertTrue(next.ecuConnected)
        assertEquals(0, next.consecutiveMisses)
    }

    @Test
    fun `miss below threshold keeps connected`() {
        val next = LiveObdConnectionPolicy.onLiveMiss(
            currentlyConnected = true,
            consecutiveMisses = 3,
            threshold = 5,
        )
        assertTrue(next.ecuConnected)
        assertEquals(4, next.consecutiveMisses)
    }

    @Test
    fun `miss at threshold marks disconnected`() {
        val next = LiveObdConnectionPolicy.onLiveMiss(
            currentlyConnected = true,
            consecutiveMisses = 4,
            threshold = 5,
        )
        assertFalse(next.ecuConnected)
        assertEquals(5, next.consecutiveMisses)
    }

    @Test
    fun `miss while already disconnected keeps disconnected and increments`() {
        val next = LiveObdConnectionPolicy.onLiveMiss(
            currentlyConnected = false,
            consecutiveMisses = 0,
            threshold = 5,
        )
        assertFalse(next.ecuConnected)
        assertEquals(1, next.consecutiveMisses)
    }

    @Test
    fun `only rpm speed and voltage count as live pids`() {
        assertTrue(LiveObdConnectionPolicy.isLivePid("0c"))
        assertTrue(LiveObdConnectionPolicy.isLivePid("0C"))
        assertTrue(LiveObdConnectionPolicy.isLivePid("0d"))
        assertTrue(LiveObdConnectionPolicy.isLivePid("42"))
        assertFalse(LiveObdConnectionPolicy.isLivePid("0b"))
        assertFalse(LiveObdConnectionPolicy.isLivePid("04"))
        assertFalse(LiveObdConnectionPolicy.isLivePid("10"))
    }
}
