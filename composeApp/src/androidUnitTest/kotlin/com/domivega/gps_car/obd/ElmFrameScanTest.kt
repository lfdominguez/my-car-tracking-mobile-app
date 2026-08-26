package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ElmFrameScanTest {

    @Test
    fun clean_stripsWhitespaceAndUpperCases() {
        assertEquals("410C1AF8", ElmFrameScan.clean("41 0c\r\n1a f8"))
    }

    @Test
    fun positiveMarker_padsToTwoDigits() {
        assertEquals("4100", ElmFrameScan.positiveMarker("0"))
        assertEquals("410C", ElmFrameScan.positiveMarker("0c"))
        assertEquals("41A6", ElmFrameScan.positiveMarker(" a6 "))
    }

    @Test
    fun markerIndices_findsEveryRespondingModule() {
        // Corolla 0100 on the functional header: engine then a second module.
        val clean = "4100BE1FA013410098188003"
        assertEquals(listOf(0, 12), ElmFrameScan.markerIndices(clean, "4100"))
    }

    @Test
    fun markerIndices_ignoresOddOffsetSpoofsWhenARealFrameExists() {
        // "41" straddling two payload bytes must not read as a frame start.
        val clean = "4140FEDCAC1141400000"
        assertEquals(listOf(0, 12), ElmFrameScan.markerIndices(clean, "4140"))
    }

    @Test
    fun markerIndices_fallsBackToTheOddOffsetMatchWhenNoAlignedOneExists() {
        // Odd-width artifact: degrade to the old single-frame behaviour rather
        // than lose the frame entirely.
        val clean = "F4100BE1FA013"
        assertEquals(listOf(1), ElmFrameScan.markerIndices(clean, "4100"))
    }

    @Test
    fun markerIndices_emptyWhenAbsent() {
        assertEquals(emptyList<Int>(), ElmFrameScan.markerIndices("410CFFFF", "4142"))
    }

    @Test
    fun markerIndex_prefersAlignedThenFallsBack() {
        assertEquals(0, ElmFrameScan.markerIndex("4100BE1FA013", "4100"))
        assertEquals(1, ElmFrameScan.markerIndex("F4100BE1FA013", "4100"))
        assertEquals(-1, ElmFrameScan.markerIndex("410CFFFF", "4142"))
    }
}
