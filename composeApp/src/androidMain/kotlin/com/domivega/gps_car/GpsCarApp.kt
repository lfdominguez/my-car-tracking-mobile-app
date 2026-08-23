package com.domivega.gps_car

import android.app.Application
import com.domivega.gps_car.data.queue.SampleUploadScheduler
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.ObdPresenceController
import com.domivega.gps_car.obd.TripLogStore
import com.domivega.gps_car.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GpsCarApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Native BLE ELM327 OBD
        ObdBleManager.initialize(this)
        // Idle 1-minute connect poll (no Companion "wait for device").
        ObdPresenceController.arm(this)

        // Auto start/stop tracking based on ECU connectivity
        EcuConnectionController.initialize(this)

        // Load persisted trip logs for the Debug Console.
        TripLogStore.initialize(this, appScope)

        // Permanent WAITING when a dongle is configured (process start / after swipe-away).
        if (WaitingFgsGate.shouldEnsureWaiting(AppSettings(this).bleDeviceAddress)) {
            startForegroundServiceCompat(ForegroundTrackingService.ACTION_START_WAITING)
        }

        // Drain leftover samples even if FGS is not running.
        SampleUploadScheduler.enqueue(this)
    }
}
