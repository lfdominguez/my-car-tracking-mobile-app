package com.domivega.gps_car

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaitingFgsGateTest {
    @Test
    fun emptyAddress_doesNotEnsureWaiting() {
        assertFalse(WaitingFgsGate.shouldEnsureWaiting(""))
        assertFalse(WaitingFgsGate.shouldEnsureWaiting("   "))
    }

    @Test
    fun configuredAddress_ensuresWaiting() {
        assertTrue(WaitingFgsGate.shouldEnsureWaiting("AA:BB:CC:DD:EE:FF"))
        assertTrue(WaitingFgsGate.shouldEnsureWaiting("  AA:BB:CC:DD:EE:FF  "))
    }
}
