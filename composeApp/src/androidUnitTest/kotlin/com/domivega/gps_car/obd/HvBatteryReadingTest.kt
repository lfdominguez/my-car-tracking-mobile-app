package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HvBatteryReadingTest {

    @Test
    fun `decodes voltage current and mode`() {
        // flags=01 rsvd=00  volts=0x5900/64 = 356.0  amps=0x0205/10 = 51.7
        val r = HvBatteryReading.fromDataHex("010059000205")!!
        assertEquals(356.0, r.packVolts, 0.001)
        assertEquals(51.7, r.packAmps, 0.001)
        assertEquals(0, r.hevMode)
    }

    @Test
    fun `current is signed so charging reads negative`() {
        // amps = 0xFDFB -> -517 -> -51.7 A, i.e. power flowing into the pack.
        val charging = HvBatteryReading.fromDataHex("01005900FDFB")!!
        assertEquals(356.0, charging.packVolts, 0.001)
        assertEquals(-51.7, charging.packAmps, 0.001)
        assertEquals(-18.405, charging.packKw, 0.001)
    }

    @Test
    fun `power is voltage times current in kW`() {
        val r = HvBatteryReading.fromDataHex("010059000205")!!
        assertEquals(356.0 * 51.7 / 1000.0, r.packKw, 0.001)
    }

    @Test
    fun `mode bits come from byte A bits 1 and 2`() {
        // A = 0b0000_0010 -> bits1..2 = 01 -> charge depleting
        assertEquals(1, HvBatteryReading.fromDataHex("020059000205")!!.hevMode)
        // A = 0b0000_0100 -> bits1..2 = 10 -> charge increasing
        assertEquals(2, HvBatteryReading.fromDataHex("040059000205")!!.hevMode)
    }

    @Test
    fun `rejects short or non-hex payloads`() {
        assertNull(HvBatteryReading.fromDataHex(null))
        assertNull(HvBatteryReading.fromDataHex(""))
        // Five bytes: one short of the layout.
        assertNull(HvBatteryReading.fromDataHex("0100590002"))
        assertNull(HvBatteryReading.fromDataHex("01005900ZZ05"))
    }

    @Test
    fun `extra trailing bytes are ignored`() {
        // Sources disagree on whether the PID is six or seven bytes; the fields we
        // read sit in the first six either way.
        val r = HvBatteryReading.fromDataHex("01005900020599")!!
        assertEquals(356.0, r.packVolts, 0.001)
        assertEquals(51.7, r.packAmps, 0.001)
    }

    @Test
    fun `mode labels cover the documented values`() {
        assertEquals("Charge sustaining", HvBatteryReading.modeLabel(0))
        assertEquals("Charge depleting", HvBatteryReading.modeLabel(1))
        assertEquals("Charge increasing", HvBatteryReading.modeLabel(2))
        assertEquals("Mode 3", HvBatteryReading.modeLabel(3))
    }
}
