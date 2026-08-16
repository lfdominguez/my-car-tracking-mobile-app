package com.domivega.gps_car.obd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PidPollPolicyTest {
    @Test
    fun miss_keepsPreviousValue() {
        val prev = mapOf("04" to 42.0, "2f" to 55.0)
        val next = PidPollPolicy.afterMiss(prev, pid = "04")
        assertEquals(42.0, next["04"])
        assertEquals(55.0, next["2f"])
    }

    @Test
    fun success_updatesValue() {
        val prev = mapOf("04" to 42.0)
        val next = PidPollPolicy.afterSuccess(prev, pid = "04", value = 10.0)
        assertEquals(10.0, next["04"])
    }

    @Test
    fun miss_onUnknownPid_doesNotInsert() {
        val prev = emptyMap<String, Double>()
        val next = PidPollPolicy.afterMiss(prev, pid = "04")
        assertNull(next["04"])
    }

    @Test
    fun linkLost_clearsLastGoodIncludingZeroSpeed() {
        val prev = mapOf("0d" to 0.0, "0c" to 800.0, "04" to 12.0)
        val next = PidPollPolicy.afterLinkLost(prev)
        assertTrue(next.isEmpty())
        assertNull(next["0d"])
        assertNull(next["0c"])
    }
}
