package com.domivega.gps_car

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObdEnableGateTest {
    @Test
    fun mayConnect_whenEnabled() {
        assertTrue(ObdEnableGate.mayConnect(obdEnabled = true))
    }

    @Test
    fun mayConnect_falseWhenDisabled() {
        assertFalse(ObdEnableGate.mayConnect(obdEnabled = false))
    }

    @Test
    fun disableRequiresConfirmation_onlyWhileTracking() {
        assertTrue(ObdEnableGate.disableRequiresConfirmation(isTracking = true))
        assertFalse(ObdEnableGate.disableRequiresConfirmation(isTracking = false))
    }
}
