package com.domivega.gps_car.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ObdDevicePresentationTest {
    @Test
    fun deviceLabel_prefersLiveLabelThenStoredThenNone() {
        assertEquals(
            "Live ELM (AA:BB)",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "Live ELM (AA:BB)",
                deviceName = "Stored",
                deviceAddress = "11:22",
            ),
        )
        assertEquals(
            "ELM327 (AA:BB:CC:DD:EE:FF)",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "",
                deviceName = "ELM327",
                deviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "   ",
                deviceName = "",
                deviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertEquals(
            "Garage dongle",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "",
                deviceName = "Garage dongle",
                deviceAddress = "",
            ),
        )
        assertEquals(
            "None selected",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "",
                deviceName = "",
                deviceAddress = "",
            ),
        )
    }
}
