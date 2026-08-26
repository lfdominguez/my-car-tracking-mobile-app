package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `return null for truncated two-byte PIDs`() {
        // A half-arrived frame used to pad the missing byte with 0, so `410C1A`
        // decoded to a plausible 1664 RPM and was committed as a real reading.
        assertNull(parser.decodePid("0C", "41 0C 1A"))
        assertNull(parser.decodePid("42", "41 42 1A"))
        assertNull(parser.decodePid("1F", "41 1F 1A"))
        assertNull(parser.decodePid("31", "41 31 1A"))
        assertNull(parser.decodePid("44", "41 44 1A"))
        assertNull(parser.decodePid("10", "41 10 1A"))
        assertNull(parser.decodePid("5E", "41 5E 1A"))
        assertNull(parser.decodePid("43", "41 43 1A"))
    }

    @Test
    fun `decodes PID 43 absolute load as two bytes`() {
        // SAE J1979: (A*256+B)*100/255, 0..25700 %.
        assertEquals(30.196, parser.decodePid("43", "41 43 00 4D")!!, 0.001)
        assertEquals(100.0, parser.decodePid("43", "41 43 00 FF")!!, 0.001)
        // Boosted engines legitimately exceed 100 %.
        assertEquals(200.0, parser.decodePid("43", "41 43 01 FE")!!, 0.001)
    }

    @Test
    fun `PID 43 no longer reads zero for every normal load`() {
        // Decoding byte A alone pinned every load at or below 100 % to 0.0 %,
        // because raw = pct*255/100 never leaves the low byte until ~100.4 %.
        val quarterLoad = parser.decodePid("43", "41 43 00 40")!!
        assertTrue(quarterLoad > 24.0 && quarterLoad < 26.0)
    }

    @Test
    fun `PID 9A is not decoded as a percentage`() {
        // 0x9A is multi-field Hybrid/EV system data (mode flags, pack voltage,
        // pack current) — never a charge percentage.
        assertNull(parser.decodePid("9A", "41 9A 01 20 00 64 00 0A"))
        assertNull(parser.decodePid("9a", "419A0120"))
    }

    @Test
    fun `marker is matched on a byte boundary`() {
        // Payload bytes 04 10 C0 spell "410C" starting at an odd nibble, ahead of
        // the real frame. Taking that hit shifted every byte by half and decoded
        // 260 RPM; the genuine frame at the even offset is the right one.
        assertEquals(1726.0, parser.decodePid("0C", "0410C0410C1AF8")!!, 0.001)
    }

    @Test
    fun `odd-offset marker still decodes when there is no byte-aligned match`() {
        // Parity can be broken by an adapter artifact; falling back to the first
        // match keeps such a frame decodable instead of regressing to null.
        assertEquals(1726.0, parser.decodePid("0C", "4410C1AF8")!!, 0.001)
        assertEquals(1726.0, parser.decodePid("0C", "SEARCHING...410C1AF8>")!!, 0.001)
    }

    @Test
    fun `still decode complete two-byte PIDs`() {
        assertEquals(1664.0, parser.decodePid("0C", "41 0C 1A 00")!!, 0.001)
        assertEquals(12.481, parser.decodePid("42", "41 42 30 C1")!!, 0.001)
    }

    @Test
    fun `single byte PIDs are unaffected by the length guard`() {
        assertEquals(34.9019, parser.decodePid("04", "41 04 59")!!, 0.001)
        assertEquals(50.0, parser.decodePid("05", "41 05 5A")!!, 0.001)
    }
}
