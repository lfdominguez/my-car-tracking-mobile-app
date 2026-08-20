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
    fun miss_onRpm_dropsLastGoodSoSamplesDoNotReuseIt() {
        val prev = mapOf("0c" to 704.0, "42" to 12.49, "04" to 34.9)
        val next = PidPollPolicy.afterMiss(prev, pid = "0c")
        assertNull(next["0c"])
        assertEquals(12.49, next["42"])
        assertEquals(34.9, next["04"])
    }

    @Test
    fun miss_onSpeed_dropsLastGood() {
        val prev = mapOf("0d" to 0.0, "0c" to 704.0)
        val next = PidPollPolicy.afterMiss(prev, pid = "0d")
        assertNull(next["0d"])
        assertEquals(704.0, next["0c"])
    }

    @Test
    fun expireOlderThan_dropsStaleLastGoodRpm() {
        val values = mapOf("0c" to 704.0, "42" to 12.49)
        val seenAt = mapOf("0c" to 1_000L, "42" to 10_000L)
        val next = PidPollPolicy.expireOlderThan(
            values,
            lastSeenAtMs = seenAt,
            nowMs = 1_000L + PidPollPolicy.MAX_AGE_MS,
        )
        assertNull(next["0c"])
        assertEquals(12.49, next["42"])
    }

    @Test
    fun expireOlderThan_keepsFreshRpm() {
        val values = mapOf("0c" to 704.0)
        val seenAt = mapOf("0c" to 1_000L)
        val next = PidPollPolicy.expireOlderThan(
            values,
            lastSeenAtMs = seenAt,
            nowMs = 1_000L + PidPollPolicy.MAX_AGE_MS - 1L,
        )
        assertEquals(704.0, next["0c"])
    }

    @Test
    fun linkLost_clearsLastGoodIncludingZeroSpeed() {
        val prev = mapOf("0d" to 0.0, "0c" to 800.0, "04" to 12.0)
        val next = PidPollPolicy.afterLinkLost(prev)
        assertTrue(next.isEmpty())
        assertNull(next["0d"])
        assertNull(next["0c"])
    }

    @Test
    fun expireOlderThan_keepsSessionLockedClusterOdometerAndOil() {
        val values = mapOf(
            OdometerReading.UDS_KM_KEY to 45012.0,
            VwClusterDids.KEY_OIL_C to 39.0,
            VwClusterDids.KEY_FUEL_PCT to 55.0,
            "0c" to 800.0,
        )
        val seenAt = mapOf(
            OdometerReading.UDS_KM_KEY to 1_000L,
            VwClusterDids.KEY_OIL_C to 1_000L,
            VwClusterDids.KEY_FUEL_PCT to 1_000L,
            "0c" to 1_000L,
        )
        val next = PidPollPolicy.expireOlderThan(
            values,
            lastSeenAtMs = seenAt,
            nowMs = 1_000L + PidPollPolicy.MAX_AGE_MS,
        )
        assertEquals(45012.0, next[OdometerReading.UDS_KM_KEY])
        assertEquals(39.0, next[VwClusterDids.KEY_OIL_C])
        assertEquals(55.0, next[VwClusterDids.KEY_FUEL_PCT])
        assertNull(next["0c"])
    }
}
