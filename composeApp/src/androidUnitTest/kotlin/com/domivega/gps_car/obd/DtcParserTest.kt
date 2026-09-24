package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtcParserTest {

    @Test
    fun `first nibble maps to P C B U and second digit`() {
        assertEquals("P0133", DtcParser.decodeDtc(0x01, 0x33))
        assertEquals("U0100", DtcParser.decodeDtc(0xC1, 0x00))
        assertEquals("C0123", DtcParser.decodeDtc(0x41, 0x23))
        assertEquals("B0111", DtcParser.decodeDtc(0x81, 0x11))
        assertEquals("P2AF0", DtcParser.decodeDtc(0x2A, 0xF0))
        assertEquals("P3000", DtcParser.decodeDtc(0x30, 0x00))
        assertEquals("U3FFF", DtcParser.decodeDtc(0xFF, 0xFF))
        assertNull(DtcParser.decodeDtc(0x00, 0x00))
    }

    @Test
    fun `can single frame with count byte`() {
        assertEquals(listOf("P0133", "P0134"), DtcParser.parseStored("43 02 01 33 01 34\r\r>"))
        // ATS0 (no spaces), as the app configures the adapter.
        assertEquals(listOf("P0133", "P0134"), DtcParser.parseStored("430201330134\r\r>"))
    }

    @Test
    fun `can count limits codes and zero count means no codes`() {
        assertEquals(listOf("P0133"), DtcParser.parseStored("43 01 01 33 01 34"))
        assertEquals(emptyList<String>(), DtcParser.parseStored("43 00\r\r>"))
    }

    @Test
    fun `legacy seven byte frame padded with zeros`() {
        assertEquals(listOf("P0133"), DtcParser.parseStored("43 01 33 00 00 00 00\r\r>"))
        assertEquals(listOf("P0133"), DtcParser.parseStored("43013300000000"))
        assertEquals(
            listOf("P0133", "C0123", "B0111"),
            DtcParser.parseStored("43 01 33 41 23 81 11"),
        )
    }

    @Test
    fun `legacy multi line answer with more than three codes`() {
        val raw = "43 01 33 01 34 01 35\r43 01 36 00 00 00 00\r\r>"
        assertEquals(listOf("P0133", "P0134", "P0135", "P0136"), DtcParser.parseStored(raw))
    }

    @Test
    fun `can multi frame without headers uses length line and frame indices`() {
        val raw = "00A\r0: 43 04 01 33 01 34\r1: 01 35 01 36 00 00 00\r\r>"
        assertEquals(listOf("P0133", "P0134", "P0135", "P0136"), DtcParser.parseStored(raw))
    }

    @Test
    fun `can multi frame without spaces and glued by ATL0`() {
        val spaced = "00A\r0:430401330134\r1:01350136000000\r\r>"
        assertEquals(listOf("P0133", "P0134", "P0135", "P0136"), DtcParser.parseStored(spaced))
        val glued = "00A0:4304013301341:01350136000000>"
        assertEquals(listOf("P0133", "P0134", "P0135", "P0136"), DtcParser.parseStored(glued))
    }

    @Test
    fun `multiple ecus without headers`() {
        val raw = "43 01 01 33\r43 02 C1 00 01 33\r43 00\r\r>"
        // Deduplicated, answer order kept.
        assertEquals(listOf("P0133", "U0100"), DtcParser.parseStored(raw))
    }

    @Test
    fun `multiple ecus with 11 bit headers`() {
        val raw = "7E8 06 43 02 01 33 01 34\r7E9 04 43 01 C1 00\r7EA 02 43 00\r\r>"
        assertEquals(listOf("P0133", "P0134", "U0100"), DtcParser.parseStored(raw))
    }

    @Test
    fun `headers without spaces`() {
        val raw = "7E806430201330134\r7E90443 01C100\r>"
        assertEquals(listOf("P0133", "P0134", "U0100"), DtcParser.parseStored(raw.replace(" ", "")))
    }

    @Test
    fun `multi frame with headers is reassembled per ecu`() {
        val raw = "7E8 10 0A 43 04 01 33 01 34\r" +
            "7E9 06 43 02 41 23 81 11\r" +
            "7E8 21 01 35 01 36 00 00 00\r\r>"
        assertEquals(
            listOf("C0123", "B0111", "P0133", "P0134", "P0135", "P0136"),
            DtcParser.parseStored(raw),
        )
    }

    @Test
    fun `29 bit headers`() {
        val raw = "18 DA F1 10 04 43 01 01 33\r\r>"
        assertEquals(listOf("P0133"), DtcParser.parseStored(raw))
    }

    @Test
    fun `no data is read with no codes`() {
        assertEquals(emptyList<String>(), DtcParser.parseStored("NO DATA\r\r>"))
        assertEquals(emptyList<String>(), DtcParser.parseStored("SEARCHING...\rNO DATA\r\r>"))
        assertEquals(emptyList<String>(), DtcParser.parsePending("NO DATA"))
    }

    @Test
    fun `adapter errors are failures`() {
        assertNull(DtcParser.parseStored("?\r\r>"))
        assertNull(DtcParser.parseStored("ERROR\r\r>"))
        assertNull(DtcParser.parseStored("CAN ERROR\r\r>"))
        assertNull(DtcParser.parseStored("BUS INIT: ...ERROR\r\r>"))
        assertNull(DtcParser.parseStored("UNABLE TO CONNECT\r\r>"))
        assertNull(DtcParser.parseStored("SEARCHING...\rUNABLE TO CONNECT\r\r>"))
        assertNull(DtcParser.parseStored("BUS BUSY\r>"))
        assertNull(DtcParser.parseStored("STOPPED\r>"))
    }

    @Test
    fun `blank garbage and negative responses are failures`() {
        assertNull(DtcParser.parseStored(null))
        assertNull(DtcParser.parseStored(""))
        assertNull(DtcParser.parseStored(">"))
        assertNull(DtcParser.parseStored("OK\r>"))
        assertNull(DtcParser.parseStored("7F 03 11\r>"))
        // A Mode 07 answer is not a Mode 03 answer.
        assertNull(DtcParser.parseStored("47 01 01 33"))
    }

    @Test
    fun `searching prefix and bus init noise are ignored`() {
        assertEquals(listOf("P0133"), DtcParser.parseStored("SEARCHING...\r43 01 01 33\r\r>"))
        assertEquals(listOf("P0133"), DtcParser.parseStored("BUS INIT: ...OK\r43 01 33 00 00 00 00\r>"))
    }

    @Test
    fun `dedup and padding codes dropped`() {
        val raw = "43 03 01 33 00 00 01 33\r43 01 33 00 00 01 33\r>"
        assertEquals(listOf("P0133"), DtcParser.parseStored(raw))
    }

    @Test
    fun `mode 07 pending uses prefix 47`() {
        assertEquals(listOf("P0420"), DtcParser.parsePending("47 01 04 20\r\r>"))
        assertEquals(listOf("P0420"), DtcParser.parsePending("47 04 20 00 00 00 00"))
        assertEquals(emptyList<String>(), DtcParser.parsePending("47 00"))
        // A stored-code answer is not a pending-code answer.
        assertNull(DtcParser.parsePending("43 01 01 33"))
    }
}
