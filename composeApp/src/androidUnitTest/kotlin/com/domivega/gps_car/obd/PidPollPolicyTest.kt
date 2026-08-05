package com.domivega.gps_car.obd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
