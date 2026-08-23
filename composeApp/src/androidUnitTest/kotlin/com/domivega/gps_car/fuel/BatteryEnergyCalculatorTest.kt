package com.domivega.gps_car.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryEnergyCalculatorTest {
    @Test
    fun socDropToKwh() {
        assertEquals(
            10.0,
            BatteryEnergyCalculator.energyKwhFromSoc(80.0, 60.0, 50.0)!!,
            1e-9,
        )
    }

    @Test
    fun chargeIsNotConsume() {
        assertNull(BatteryEnergyCalculator.energyKwhFromSoc(50.0, 60.0, 50.0))
    }
}
