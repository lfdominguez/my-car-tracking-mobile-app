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
        // Unverified MQB placeholders expected in default probe list
        assertTrue(fromNull.contains(0x22B0) || fromNull.contains(0x2203) || fromNull.contains(0x029F))
    }

    @Test
    fun `candidateDids ignores invalid override and returns built-in list`() {
        val defaults = UdsReadDid.candidateDids(null)
        assertEquals(defaults, UdsReadDid.candidateDids("ZZ"))
        assertEquals(defaults, UdsReadDid.candidateDids("22B00"))
        assertEquals(defaults, UdsReadDid.candidateDids("GGGG"))
    }
}
