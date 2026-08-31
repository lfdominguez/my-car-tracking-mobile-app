package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmInitPolicyTest {

    @Test
    fun `CAN functional header skipped only for Golf mk4 TDI`() {
        assertFalse(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.VwGolfMk4Tdi))
        assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.Generic))
        assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.VwMqb))
        assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.Mg3HybridPlus))
    }

    @Test
    fun `MG3 widens only the single-responder ST ceiling`() {
        assertEquals("FF", ElmInitPolicy.singleResponderStHex(VehicleObdProfile.Mg3HybridPlus))
        assertEquals(
            ElmPerformanceMode.ST_SINGLE_RESPONDER_HEX,
            ElmInitPolicy.singleResponderStHex(VehicleObdProfile.Generic),
        )
        assertEquals(
            ElmPerformanceMode.ST_SINGLE_RESPONDER_HEX,
            ElmInitPolicy.singleResponderStHex(VehicleObdProfile.VwMqb),
        )
        // The functional tier stays generic for every profile: widening it would be
        // paid on every poll of every round, not only by an unanswered PID.
        assertEquals("64", ElmPerformanceMode.ST_FUNCTIONAL_HEX)
    }
}
