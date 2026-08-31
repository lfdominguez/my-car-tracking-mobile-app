package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleObdProfileTest {
    @Test
    fun `display names match product labels`() {
        assertEquals("Generic OBD", VehicleObdProfile.Generic.displayName)
        assertEquals("VW MQB (Nivus)", VehicleObdProfile.VwMqb.displayName)
        assertEquals("VW Golf mk4 TDI (ASZ)", VehicleObdProfile.VwGolfMk4Tdi.displayName)
        assertEquals("MG3 Hybrid+ (1.5 HEV)", VehicleObdProfile.Mg3HybridPlus.displayName)
    }

    @Test
    fun `fromName parses known and defaults unknown to Generic`() {
        assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.fromName("Generic"))
        assertEquals(VehicleObdProfile.VwMqb, VehicleObdProfile.fromName("VwMqb"))
        assertEquals(VehicleObdProfile.VwMqb, VehicleObdProfile.fromName("vwmqb"))
        assertEquals(VehicleObdProfile.VwGolfMk4Tdi, VehicleObdProfile.fromName("VwGolfMk4Tdi"))
        assertEquals(VehicleObdProfile.VwGolfMk4Tdi, VehicleObdProfile.fromName("vwgolfmk4tdi"))
        assertEquals(VehicleObdProfile.Mg3HybridPlus, VehicleObdProfile.fromName("Mg3HybridPlus"))
        assertEquals(VehicleObdProfile.Mg3HybridPlus, VehicleObdProfile.fromName("mg3hybridplus"))
        assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.fromName("NOPE"))
        assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.fromName(""))
    }

    @Test
    fun `default profile is Generic`() {
        assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.DEFAULT)
        assertEquals("Generic", VehicleObdProfile.DEFAULT.name)
    }
}
