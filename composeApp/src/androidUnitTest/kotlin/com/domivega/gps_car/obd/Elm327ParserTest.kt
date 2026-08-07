package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Elm327ParserTest {

    private val parser = Elm327Parser()

    @Test
    fun `parse RPM from response containing 410C1AF8`() {
        val result = parser.decodePid("0C", "41 0C 1A F8")
        assertEquals(1726.0, result!!, 0.001)
    }

    @Test
    fun `parse speed 410D32`() {
        val result = parser.decodePid("0D", "41 0D 32")
        assertEquals(50.0, result!!, 0.001)
    }

    @Test
    fun `parse load 41047F`() {
        val result = parser.decodePid("04", "41047F")
        assertEquals(49.8039, result!!, 0.001)
    }

    @Test
    fun `handle multi-line and noise`() {
        val response = """
            SEARCHING...
            41 0C 1A F8
            >
        """.trimIndent()
        val result = parser.decodePid("0C", response)
        assertEquals(1726.0, result!!, 0.001)
    }

    @Test
    fun `handle spaces and mixed case PID`() {
        val result = parser.decodePid("0c", "410c1AF8")
        assertEquals(1726.0, result!!, 0.001)
    }

    @Test
    fun `return null on NO DATA`() {
        assertNull(parser.decodePid("0C", "NO DATA"))
    }

    @Test
    fun `return null on ERROR`() {
        assertNull(parser.decodePid("0C", "CAN ERROR"))
    }

    @Test
    fun `return null on empty response`() {
        assertNull(parser.decodePid("0C", ""))
    }

    @Test
    fun `parse temperature 05`() {
        // 41 05 7B -> 123 - 40 = 83
        val result = parser.decodePid("05", "41 05 7B")
        assertEquals(83.0, result!!, 0.001)
    }

    @Test
    fun `parse voltage 42`() {
        // 41 42 36 B0 -> (14000) / 1000 = 14.0
        // 36B0 hex is 14000
        val result = parser.decodePid("42", "41 42 36 B0")
        assertEquals(14.0, result!!, 0.001)
    }

    @Test
    fun `parse distance since codes cleared PID 31`() {
        // 41 31 16 44 -> 0x1644 = 5700 km
        val result = parser.decodePid("31", "41 31 16 44")
        assertEquals(5700.0, result!!, 0.001)
    }

    @Test
    fun `parse vehicle odometer PID A6`() {
        // 20000.0 km -> raw 200000 = 0x00030D40
        val result = parser.decodePid("A6", "41 A6 00 03 0D 40")
        assertEquals(20000.0, result!!, 0.001)
    }

    @Test
    fun `parse vehicle odometer PID A6 with lowercase and no spaces`() {
        val result = parser.decodePid("a6", "41a600030d40")
        assertEquals(20000.0, result!!, 0.001)
    }

    @Test
    fun `return null for incomplete A6 payload`() {
        assertNull(parser.decodePid("A6", "41 A6 00 03"))
    }

    @Test
    fun `decodes PID 5E engine fuel rate L per hour`() {
        // (A*256+B)*0.05 → A=0x01 B=0xF4 = 500 → 25.0 L/h
        val result = parser.decodePid("5e", "41 5E 01 F4")
        assertEquals(25.0, result!!, 0.001)
    }

    @Test
    fun `WWH RPM bytes decode equal to Mode 01 via compatibility shim`() {
        val mode01 = parser.decodePid("0c", "410C1AF8")
        val wwhShim = WwhObd.mode01CompatibleResponse("0c", "62F40C1AF8>")
        val fromWwh = parser.decodePid("0c", wwhShim!!)
        assertEquals(mode01, fromWwh)
        assertEquals(1726.0, fromWwh!!, 0.001)
    }
}
