package com.domivega.gps_car.obd

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class PidSupportTest {
    @Test
    fun parse0100_classicExample() {
        // Common example: 41 00 BE 1F A8 13 → many base PIDs including 0C/0D
        val supported = PidSupport.parseSupportBitmap(0x00, "4100BE1FA813>")
        assertTrue(0x0C in supported) // RPM
        assertTrue(0x0D in supported) // speed
        assertTrue(0x04 in supported) // load
        assertTrue(0x05 in supported) // coolant
        // Bit for "more PIDs" (0x20) often set in BE...
        assertTrue(0x20 in supported)
    }

    @Test
    fun nextSupportCommand_whenBit20Set() {
        val raw = "4100BE1FA813>"
        assertEquals(0x20, PidSupport.nextSupportCommandPid(0x00, raw))
    }

    @Test
    fun nextSupportCommand_nullWhenNoMore() {
        // Last bit clear: craft bitmap with no PID 0x20
        // All zeros except PID 01
        val raw = "410080000000>"
        assertNull(PidSupport.nextSupportCommandPid(0x00, raw))
        assertTrue(0x01 in PidSupport.parseSupportBitmap(0x00, raw))
        assertFalse(0x20 in PidSupport.parseSupportBitmap(0x00, raw))
    }

    @Test
    fun isMode01Supported_filters() {
        val supported = setOf(0x0C, 0x0D, 0xA6)
        assertTrue(PidSupport.isMode01Supported(supported, "0c"))
        assertTrue(PidSupport.isMode01Supported(supported, "A6"))
        assertFalse(PidSupport.isMode01Supported(supported, "10"))
        // manufacturer PID allowed by default
        assertTrue(PidSupport.isMode01Supported(supported, "ff125a"))
    }

    @Test
    fun emptySupported_allowsAllUntilDiscovery() {
        assertTrue(PidSupport.isMode01Supported(emptySet(), "0c"))
    }

    @Test
    fun noData_returnsEmpty() {
        assertTrue(PidSupport.parseSupportBitmap(0x00, "NO DATA").isEmpty())
    }
}
