package com.domivega.gps_car.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmInitPolicyTest {

    @Test
    fun `CAN functional header skipped only for Golf mk4 TDI`() {
        assertFalse(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.VwGolfMk4Tdi))
        assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.Generic))
        assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.VwMqb))
    }
}
