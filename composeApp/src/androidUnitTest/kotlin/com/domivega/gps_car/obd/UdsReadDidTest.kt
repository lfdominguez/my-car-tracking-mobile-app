package com.domivega.gps_car.obd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsReadDidTest {

    @Test
    fun `parsePositiveReadDid finds 62 plus DID in messy ELM response`() {
        val did = 0x22B0
        val raw = """
            SEARCHING...
            62 22 B0 00 01 E2 40
            >
        """.trimIndent()

        val payload = UdsReadDid.parsePositiveReadDid(raw, did)

        assertNotNull(payload)
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0xE2.toByte(), 0x40),
            payload,
        )
    }

    @Test
    fun `parsePositiveReadDid handles lowercase and no spaces`() {
        val payload = UdsReadDid.parsePositiveReadDid("6222b00001e240", 0x22B0)
        assertNotNull(payload)
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0xE2.toByte(), 0x40),
            payload,
        )
    }

    @Test
    fun `parsePositiveReadDid rejects NO DATA`() {
        assertNull(UdsReadDid.parsePositiveReadDid("NO DATA", 0x22B0))
    }

    @Test
    fun `parsePositiveReadDid rejects negative UDS response`() {
        // 7F 22 31 = negative response to service 22, NRC requestOutOfRange
        assertNull(UdsReadDid.parsePositiveReadDid("7F 22 31", 0x22B0))
    }

    @Test
    fun `parsePositiveReadDid rejects wrong DID`() {
        assertNull(UdsReadDid.parsePositiveReadDid("62 22 B0 00 01 E2 40", 0x2203))
    }

    @Test
    fun `parsePositiveReadDid rejects empty response`() {
        assertNull(UdsReadDid.parsePositiveReadDid("", 0x22B0))
        assertNull(UdsReadDid.parsePositiveReadDid("   ", 0x22B0))
    }

    @Test
    fun `parsePositiveReadDid reassembles multi-frame ELM ISO-TP lines`() {
        // Naive hex-filter concatenates "014" + "0" + "1" frame indices and breaks DID payload.
        // 0x0003DA0E / 10 = 25243.0 km (Nivus-style 4-byte tenths).
        val raw = """
            014
            0: 62 22 03 00 03
            1: DA 0E 00 00 00
            >
        """.trimIndent()

        val payload = UdsReadDid.parsePositiveReadDid(raw, 0x2203)

        assertNotNull(payload)
        assertTrue(payload!!.size >= 4)
        assertEquals(0x00.toByte(), payload[0])
        assertEquals(0x03.toByte(), payload[1])
        assertEquals(0xDA.toByte(), payload[2])
        assertEquals(0x0E.toByte(), payload[3])
        assertEquals(25243.0, UdsReadDid.decodeOdometerKm(payload)!!, 0.001)
    }

    @Test
    fun `parsePositiveReadDid handles multi-frame without leading length line`() {
        val raw = """
            0: 62 22 03 00 03
            1: DA 0E
            >
        """.trimIndent()
        val payload = UdsReadDid.parsePositiveReadDid(raw, 0x2203)
        assertNotNull(payload)
        assertEquals(25243.0, UdsReadDid.decodeOdometerKm(payload!!)!!, 0.001)
    }

    @Test
    fun `parsePositiveReadDid handles ATL0 one-line multi-frame with inline frame indices`() {
        // With ATL0, clones often emit multi-frame on one line (CR or none), e.g.:
        // 0140:62220300031:DA0E000000>
        // Frame markers must not be concatenated into the hex payload.
        val raw = "0140:62220300031:DA0E000000>"
        val payload = UdsReadDid.parsePositiveReadDid(raw, 0x2203)
        assertNotNull(payload)
        assertEquals(25243.0, UdsReadDid.decodeOdometerKm(payload!!)!!, 0.001)
    }

    @Test
    fun `parsePositiveReadDid handles ATL0 spaced one-line multi-frame`() {
        val raw = "0: 62 22 03 00 03 1: DA 0E 00 00>"
        val payload = UdsReadDid.parsePositiveReadDid(raw, 0x2203)
        assertNotNull(payload)
        assertEquals(25243.0, UdsReadDid.decodeOdometerKm(payload!!)!!, 0.001)
    }

    @Test
    fun `parsePositiveReadDid handles CR-separated ATL0 multi-frame without LF`() {
        val raw = "014\r0:6222030003\r1:DA0E000000\r>"
        val payload = UdsReadDid.parsePositiveReadDid(raw, 0x2203)
        assertNotNull(payload)
        assertEquals(25243.0, UdsReadDid.decodeOdometerKm(payload!!)!!, 0.001)
    }

    @Test
    fun `decodeOdometerKm decodes 4-byte big-endian with 0_1 km resolution`() {
        // 123456 raw → 12345.6 km when interpreted as /10
        val payload = byteArrayOf(0x00, 0x01, 0xE2.toByte(), 0x40)
        val km = UdsReadDid.decodeOdometerKm(payload)
        assertNotNull(km)
        assertEquals(12345.6, km!!, 0.001)
    }

    @Test
    fun `decodeOdometerKm decodes 3-byte big-endian as whole km`() {
        // 0x01E240 = 123456 km as integer km (3-byte layout)
        val payload = byteArrayOf(0x01, 0xE2.toByte(), 0x40)
        val km = UdsReadDid.decodeOdometerKm(payload)
        assertNotNull(km)
        assertEquals(123456.0, km!!, 0.001)
    }

    @Test
    fun `decodeOdometerKm returns null for empty or too-short payload`() {
        assertNull(UdsReadDid.decodeOdometerKm(byteArrayOf()))
        assertNull(UdsReadDid.decodeOdometerKm(byteArrayOf(0x01)))
        assertNull(UdsReadDid.decodeOdometerKm(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun `decodeOdometerKm uses first four bytes when payload longer`() {
        // Extra trailing padding must not reject a valid 4-byte odometer.
        // 0x0003DA0E / 10 = 25243.0
        val payload = byteArrayOf(0x00, 0x03, 0xDA.toByte(), 0x0E, 0x00, 0x00)
        assertEquals(25243.0, UdsReadDid.decodeOdometerKm(payload)!!, 0.001)
    }

    @Test
    fun `candidateDids returns override when four hex chars`() {
        assertEquals(listOf(0x22B0), UdsReadDid.candidateDids("22B0"))
        assertEquals(listOf(0x22B0), UdsReadDid.candidateDids("22b0"))
        assertEquals(listOf(0x029F), UdsReadDid.candidateDids("0x029F"))
    }

    @Test
    fun `candidateDids returns built-in list for null or blank override`() {
        val fromNull = UdsReadDid.candidateDids(null)
        val fromEmpty = UdsReadDid.candidateDids("")
        val fromBlank = UdsReadDid.candidateDids("   ")

        assertTrue(fromNull.isNotEmpty())
        assertEquals(fromNull, fromEmpty)
        assertEquals(fromNull, fromBlank)
        // Nivus-confirmed DID first, then fallbacks
        assertEquals(0x2203, fromNull.first())
        assertTrue(fromNull.contains(0x22B0) || fromNull.contains(0x029F))
    }

    @Test
    fun `candidateDids ignores invalid override and returns built-in list`() {
        val defaults = UdsReadDid.candidateDids(null)
        assertEquals(defaults, UdsReadDid.candidateDids("ZZ"))
        assertEquals(defaults, UdsReadDid.candidateDids("22B00"))
        assertEquals(defaults, UdsReadDid.candidateDids("GGGG"))
    }

    @Test
    fun `suggestsIsoTpFlowControlRetry true for incomplete multi-frame with 62 fragment`() {
        // First frame only — no complete parseable payload; FC may unlock CF frames.
        assertTrue(UdsReadDid.suggestsIsoTpFlowControlRetry("014\r0: 62 22 03 00 03\r>"))
        assertTrue(UdsReadDid.suggestsIsoTpFlowControlRetry("0:6222030003>"))
    }

    @Test
    fun `suggestsIsoTpFlowControlRetry false for NO DATA or complete response`() {
        assertTrue(!UdsReadDid.suggestsIsoTpFlowControlRetry("NO DATA >"))
        assertTrue(!UdsReadDid.suggestsIsoTpFlowControlRetry(null))
        assertTrue(!UdsReadDid.suggestsIsoTpFlowControlRetry(""))
        // Complete single-frame style — no need for custom FC.
        assertTrue(
            !UdsReadDid.suggestsIsoTpFlowControlRetry("62 22 03 00 03 DA 0E 00 00 >"),
        )
    }
}
