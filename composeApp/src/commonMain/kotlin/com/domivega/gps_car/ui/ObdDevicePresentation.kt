package com.domivega.gps_car.ui

object ObdDevicePresentation {
    fun deviceLabel(
        liveLabel: String,
        deviceName: String,
        deviceAddress: String,
        noneLabel: String = "None selected",
    ): String {
        val live = liveLabel.trim()
        if (live.isNotEmpty()) return live
        return when {
            deviceName.isNotBlank() && deviceAddress.isNotBlank() ->
                "$deviceName ($deviceAddress)"
            deviceAddress.isNotBlank() -> deviceAddress
            deviceName.isNotBlank() -> deviceName
            else -> noneLabel
        }
    }
}
