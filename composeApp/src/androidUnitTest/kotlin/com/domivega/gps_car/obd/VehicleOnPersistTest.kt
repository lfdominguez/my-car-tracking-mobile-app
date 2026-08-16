package com.domivega.gps_car.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleOnPersistTest {

    @Test
    fun `trip end does not clear the flag`() {
        assertTrue(VehicleOnPersist.flagAfterTripEnd(sawPositiveRpm = true))
        assertFalse(VehicleOnPersist.flagAfterTripEnd(sawPositiveRpm = false))
    }

    @Test
    fun `shutdown clears the flag`() {
        assertFalse(VehicleOnPersist.flagAfterShutdown())
    }

    @Test
    fun `dongle address change clears the flag`() {
        assertFalse(
            VehicleOnPersist.shouldKeepFlag(
                previousAddress = "AA:BB",
                newAddress = "CC:DD",
            ),
        )
        assertTrue(
            VehicleOnPersist.shouldKeepFlag(
                previousAddress = "AA:BB",
                newAddress = "AA:BB",
            ),
        )
        assertTrue(
            VehicleOnPersist.shouldKeepFlag(
                previousAddress = "",
                newAddress = "AA:BB",
            ),
        )
    }
}
