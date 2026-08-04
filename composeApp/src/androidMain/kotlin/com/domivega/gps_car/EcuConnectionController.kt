package com.domivega.gps_car

import android.content.Context
import android.util.Log
import com.domivega.gps_car.obd.ObdBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observes native OBD ECU connection status and automatically starts/stops
 * ForegroundTrackingService accordingly.
 */
object EcuConnectionController {
    private const val TAG = "EcuCtl"

    @Volatile private var initialized = false
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext
            initialized = true

            startCollecting()
        }
    }

    private fun startCollecting() {
        scope.launch {
            ObdBleManager.ecuConnected.collectLatest { connected ->
                val prefs = appContext.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
                val isRunning = prefs.getString(ForegroundTrackingService.KEY_TRACKING_ID, null) != null

                if (connected && !isRunning) {
                    Log.i(TAG, "ECU connected → starting tracking service")
                    appContext.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START)
                } else if (!connected && isRunning) {
                    Log.i(TAG, "ECU disconnected → stopping tracking service")
                    appContext.startForegroundServiceCompat(ForegroundTrackingService.ACTION_STOP)
                }
            }
        }
    }
}
