package com.domivega.gps_car

import android.app.Application
import com.domivega.gps_car.data.queue.SampleUploadScheduler
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.ObdPresenceController

class GpsCarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Native BLE ELM327 OBD
        ObdBleManager.initialize(this)
        // Presence wake or cheap fallback — not a 7s reconnect hammer.
        ObdPresenceController.arm(this)

        // Auto start/stop tracking based on ECU connectivity
        EcuConnectionController.initialize(this)

        // Drain any leftover samples without requiring a forever WAITING FGS.
        SampleUploadScheduler.enqueue(this)
    }
}
