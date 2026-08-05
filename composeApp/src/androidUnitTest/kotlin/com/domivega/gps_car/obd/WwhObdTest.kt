package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WwhObdTest {
    @Test
    fun `did and command for RPM`() {
        assertEquals(0xF40C, WwhObd.didForPid(0x0C))
        assertEquals("22F40C", WwhObd.commandForPidHex("0c"))
        assertEquals("22F40D", WwhObd.commandForPidHex("0D"))
    }

    @Test
    fun `isPositiveRead detects 62F4xx and rejects NO DATA and NRC`() {
        assertTrue(WwhObd.isPositiveRead("62F40C1AF8>", expectPid = 0x0C))
        assertTrue(WwhObd.isPositiveRead("62 F4 0C 1A F8", expectPid = 0x0C))
        assertFalse(WwhObd.isPositiveRead("NO DATA", expectPid = 0x0C))
        assertFalse(WwhObd.isPositiveRead("7F2211", expectPid = 0x0C))
        assertFalse(WwhObd.isPositiveRead("62F40D00", expectPid = 0x0C))
    }

    @Test
    fun `dataHexAfterPositive strips header bytes`() {
        assertEquals("1AF8", WwhObd.dataHexAfterPositive("62F40C1AF8>", expectPid = 0x0C))
        assertEquals("1AF8", WwhObd.dataHexAfterPositive("62 F4 0C 1A F8\r>", expectPid = 0x0C))
        assertNull(WwhObd.dataHexAfterPositive("NO DATA", expectPid = 0x0C))
    }

    @Test
    fun `mode01CompatibleResponse builds 41 frame for Elm327Parser`() {
        assertEquals("410C1AF8", WwhObd.mode01CompatibleResponse("0c", "62F40C1AF8>"))
        assertNull(WwhObd.mode01CompatibleResponse("0c", "NO DATA"))
    }
}
