package com.domivega.gps_car

import android.app.Application
import com.domivega.gps_car.data.queue.SampleUploadScheduler
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.ObdPresenceController
import com.domivega.gps_car.settings.AppSettings

class GpsCarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Native BLE ELM327 OBD
        ObdBleManager.initialize(this)
        // Idle 1-minute connect poll (no Companion "wait for device").
        ObdPresenceController.arm(this)

        // Auto start/stop tracking based on ECU connectivity
        EcuConnectionController.initialize(this)

        // Permanent WAITING when a dongle is configured (process start / after swipe-away).
        if (AppSettings(this).bleDeviceAddress.trim().isNotEmpty()) {
            startForegroundServiceCompat(ForegroundTrackingService.ACTION_START_WAITING)
        }

        // Drain leftover samples even if FGS is not running.
        SampleUploadScheduler.enqueue(this)
    }
}
