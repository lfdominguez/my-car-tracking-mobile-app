package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VwClusterDidsTest {

    @Test
    fun `constants match design DIDs and keys`() {
        assertEquals(0x2206, VwClusterDids.DID_FUEL_LEVEL)
        assertEquals(0x202F, VwClusterDids.DID_OIL_TEMP)
        assertEquals(0x220D, VwClusterDids.DID_DOOR_STATUS)
        assertEquals("ffuelpct", VwClusterDids.KEY_FUEL_PCT)
        assertEquals("ffoilc", VwClusterDids.KEY_OIL_C)
        assertEquals("ffdoors", VwClusterDids.KEY_DOORS)
    }

    @Test
    fun `decodeFuelPercent Mode01-style byte`() {
        // 0x80 → ~50.2%
        val pct = VwClusterDids.decodeFuelPercent(byteArrayOf(0x80.toByte()))
        assertEquals(100.0 * 0x80 / 255.0, pct!!, 0.01)
    }

    @Test
    fun `decodeFuelPercent scaled first byte`() {
        val pct = VwClusterDids.decodeFuelPercent(byteArrayOf(0x37)) // 55
        assertEquals(100.0 * 55 / 255.0, pct!!, 0.01)
    }

    @Test
    fun `decodeFuelPercent rejects empty`() {
        assertNull(VwClusterDids.decodeFuelPercent(byteArrayOf()))
    }

    @Test
    fun `decodeOilTempC A minus 40`() {
        // 0x82 → 90°C
        assertEquals(90.0, VwClusterDids.decodeOilTempC(byteArrayOf(0x82.toByte()))!!, 0.01)
    }

    @Test
    fun `decodeOilTempC rejects empty`() {
        assertNull(VwClusterDids.decodeOilTempC(byteArrayOf()))
    }

    @Test
    fun `decodeDoorBitfield packs bytes big-endian`() {
        assertEquals(0x12L, VwClusterDids.decodeDoorBitfield(byteArrayOf(0x12)))
        assertEquals(0x1234L, VwClusterDids.decodeDoorBitfield(byteArrayOf(0x12, 0x34)))
    }

    @Test
    fun `doorSummary closed when zero`() {
        val s = VwClusterDids.doorSummary(0L)
        assertEquals("Doors closed", s)
    }

    @Test
    fun `doorSummary lists known bits`() {
        // bit0 driver, bit1 passenger (UNVERIFIED labels in code)
        val s = VwClusterDids.doorSummary(0b0011)
        assertTrue(s.contains("Driver") || s.contains("driver"))
        assertTrue(s.contains("Passenger") || s.contains("passenger") || s.contains("open"))
    }

    @Test
    fun `extraDidsInPollOrder is fuel oil doors`() {
        assertEquals(
            listOf(0x2206, 0x202F, 0x220D),
            VwClusterDids.EXTRA_DIDS,
        )
    }
}
