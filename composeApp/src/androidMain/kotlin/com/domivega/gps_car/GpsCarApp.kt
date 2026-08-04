package com.domivega.gps_car

import android.app.Application
import com.domivega.gps_car.obd.ObdBleManager

class GpsCarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Native BLE ELM327 OBD
        ObdBleManager.initialize(this)
        ObdBleManager.startAutoReconnect()

        // Auto start/stop tracking based on ECU connectivity
        EcuConnectionController.initialize(this)
    }
}
