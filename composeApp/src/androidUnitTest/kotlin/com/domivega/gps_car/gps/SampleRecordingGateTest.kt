package com.domivega.gps_car.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleRecordingGateTest {

    @Test
    fun `disconnected OBD never records`() {
        assertEquals(
            SampleTickDecision.PAUSED_OBD_DISCONNECTED,
            SampleRecordingGate.decide(ecuConnected = false, vehicleOn = true),
        )
        assertFalse(SampleRecordingGate.shouldRecord(ecuConnected = false, vehicleOn = false))
    }

    @Test
    fun `vehicle off during 90s trip grace never records`() {
        assertEquals(
            SampleTickDecision.PAUSED_VEHICLE_OFF,
            SampleRecordingGate.decide(ecuConnected = true, vehicleOn = false),
        )
    }

    /**
     * A running engine records every tick regardless of GPS: idling at a light is
     * exactly when fuel burn, coolant warm-up and voltage sag are worth capturing.
     */
    @Test
    fun `connected vehicle on records`() {
        assertEquals(
            SampleTickDecision.RECORD,
            SampleRecordingGate.decide(ecuConnected = true, vehicleOn = true),
        )
        assertTrue(SampleRecordingGate.shouldRecord(ecuConnected = true, vehicleOn = true))
    }
}
