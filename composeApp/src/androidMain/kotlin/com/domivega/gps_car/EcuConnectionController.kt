package com.domivega.gps_car

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.domivega.gps_car.obd.EcuTrackingAction
import com.domivega.gps_car.obd.EcuTrackingGate
import com.domivega.gps_car.obd.ObdBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Observes native OBD ECU connection status and automatically starts/stops
 * ForegroundTrackingService accordingly.
 *
 * Start is immediate on ECU online. Stop waits for a sustained disconnect grace
 * so transient UDS/ELM blips do not split one drive into multiple trips.
 *
 * Reconcile is **level-based**: if tracking is active while ECU is already offline
 * (manual start, sticky resume, missed edge), grace still arms and eventually shuts down.
 */
object EcuConnectionController {
    private const val TAG = "EcuCtl"
    private const val RECONCILE_POLL_MS = 5_000L

    @Volatile private var initialized = false
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val evaluateMutex = Mutex()

    @Volatile
    private var disconnectStartedAtMs: Long? = null

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ForegroundTrackingService.KEY_TRACKING_ID) {
                scope.launch { reconcile(reason = "tracking_prefs") }
            }
        }

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext
            initialized = true

            val prefs = trackingPrefs()
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)

            startCollecting()
            startReconcileLoop()
            scope.launch { reconcile(reason = "init") }
        }
    }

    private fun trackingPrefs(): SharedPreferences =
        appContext.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)

    private fun isTrackingActive(): Boolean =
        trackingPrefs().getString(ForegroundTrackingService.KEY_TRACKING_ID, null) != null

    private fun startCollecting() {
        scope.launch {
            ObdBleManager.ecuConnected.collectLatest {
                reconcile(reason = "ecu_flow")
            }
        }
    }

    private fun startReconcileLoop() {
        // Backup when prefs listener misses a write or process resumes mid-trip.
        scope.launch {
            while (isActive) {
                delay(RECONCILE_POLL_MS)
                reconcile(reason = "poll")
            }
        }
    }

    private suspend fun reconcile(reason: String) {
        evaluateMutex.withLock {
            val connected = ObdBleManager.ecuConnected.value
            val tracking = isTrackingActive()
            val priorGrace = disconnectStartedAtMs
            val decision = EcuTrackingGate.evaluate(
                ecuConnected = connected,
                isTracking = tracking,
                disconnectStartedAtMs = priorGrace,
                nowMs = System.currentTimeMillis(),
            )
            disconnectStartedAtMs = decision.disconnectStartedAtMs

            if (decision.disconnectStartedAtMs != null &&
                priorGrace == null &&
                decision.action == EcuTrackingAction.None
            ) {
                Log.i(
                    TAG,
                    "ECU offline while tracking — grace " +
                        "${EcuTrackingGate.DEFAULT_STOP_GRACE_MS / 1000}s before stop ($reason)",
                )
            }

            when (decision.action) {
                EcuTrackingAction.EnsureStart -> {
                    // Poll backup must not spam START every 5s while already tracking.
                    // Still assert START on ECU edges / init / prefs to recover WAITING+prefs.
                    if (tracking && reason == "poll") return@withLock
                    Log.i(
                        TAG,
                        "ECU connected → ensure tracking service START " +
                            "(wasTracking=$tracking reason=$reason)",
                    )
                    appContext.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START)
                }
                EcuTrackingAction.EndTrip -> {
                    // Re-check: ECU may have returned during evaluate.
                    // STOP ends the trip but keeps WAITING + permanent notification
                    // so idle OBD reconnect stays alive without opening the app.
                    if (!ObdBleManager.ecuConnected.value && isTrackingActive()) {
                        Log.i(TAG, "ECU disconnected past grace → end trip, stay paused ($reason)")
                        appContext.startForegroundServiceCompat(
                            ForegroundTrackingService.ACTION_STOP,
                        )
                    }
                }
                EcuTrackingAction.None -> Unit
            }
        }
    }
}
