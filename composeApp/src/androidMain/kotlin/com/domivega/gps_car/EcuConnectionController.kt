package com.domivega.gps_car

import android.content.Context
import android.util.Log
import com.domivega.gps_car.obd.EcuTrackingGate
import com.domivega.gps_car.obd.ObdBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Observes native OBD ECU connection status and automatically starts/stops
 * ForegroundTrackingService accordingly.
 *
 * Start is immediate on ECU online. Stop waits for a sustained disconnect grace
 * so transient UDS/ELM blips do not split one drive into multiple trips.
 */
object EcuConnectionController {
    private const val TAG = "EcuCtl"
    private const val GRACE_POLL_MS = 5_000L

    @Volatile private var initialized = false
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var disconnectStartedAtMs: Long? = null

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

                if (connected) {
                    disconnectStartedAtMs = null
                    if (!isRunning) {
                        Log.i(TAG, "ECU connected → starting tracking service")
                        appContext.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START)
                    }
                    return@collectLatest
                }

                // ECU offline: do not stop immediately — wait for sustained disconnect.
                if (!isRunning) {
                    disconnectStartedAtMs = null
                    return@collectLatest
                }

                val started = disconnectStartedAtMs ?: System.currentTimeMillis().also {
                    disconnectStartedAtMs = it
                    Log.i(
                        TAG,
                        "ECU disconnected — grace ${EcuTrackingGate.DEFAULT_STOP_GRACE_MS / 1000}s " +
                            "before stopping trip",
                    )
                }

                while (isActive && disconnectStartedAtMs == started) {
                    val now = System.currentTimeMillis()
                    if (EcuTrackingGate.shouldStopForDisconnect(disconnectStartedAtMs, now)) {
                        // Re-check still offline and still tracking.
                        val stillRunning =
                            prefs.getString(ForegroundTrackingService.KEY_TRACKING_ID, null) != null
                        if (!ObdBleManager.ecuConnected.value && stillRunning) {
                            Log.i(TAG, "ECU disconnected past grace → shutting down tracking service")
                            // Full shutdown (not forever WAITING) to cut idle battery.
                            appContext.startForegroundServiceCompat(ForegroundTrackingService.ACTION_SHUTDOWN)
                        }
                        disconnectStartedAtMs = null
                        break
                    }
                    delay(GRACE_POLL_MS)
                }
            }
        }
    }
}
