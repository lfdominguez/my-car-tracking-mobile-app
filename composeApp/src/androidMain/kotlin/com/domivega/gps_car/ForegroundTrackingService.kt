package com.domivega.gps_car

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import androidx.core.content.edit
import com.domivega.gps_car.data.queue.SampleQueueRepository
import com.domivega.gps_car.data.queue.SampleQueueUploader
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.network.Sample
import com.domivega.gps_car.obd.FuelLevelReading
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.OdometerReading
import com.domivega.gps_car.settings.AppSettings
import androidx.core.net.toUri
import com.domivega.gps_car.gps.StationaryPositionFilter
import com.domivega.gps_car.models.data.LocationData
import com.domivega.gps_car.objects.GpsDataSource

private enum class TrackingState {
    WAITING,
    TRACKING
}

/**
 * Foreground service that periodically (every ~2s) records GPS and acceleration and sends to HTTPS.
 */
class ForegroundTrackingService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var accelValues: FloatArray? = null
    // Keep most recent GPS location to pair with acceleration-triggered events
    private var lastLocation: Location? = null
    private var trackingId: String? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var repo: TrackingRepository
    private lateinit var queueRepo: SampleQueueRepository
    private lateinit var uploader: SampleQueueUploader

    private var locationJob: Job? = null
    private val accelMutex = Mutex()
    private val isStartingSession = AtomicBoolean(false)
    private var lastSessionStartAttempt = 0L

    private var currentState: TrackingState = TrackingState.WAITING

    // Tweakable thresholds (conservative defaults)
    private val maxAccurancyMetters = 15.0        // require reasonably accurate GNSS
    private val stationaryFilter = StationaryPositionFilter()


    override fun onCreate() {
        super.onCreate()

        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        val api = ApiClient(AppSettings(this))
        repo = TrackingRepository(api)
        queueRepo = SampleQueueRepository(this)
        uploader = SampleQueueUploader(this, api)
        // Start while service is alive (including WAITING) so leftover queue can drain.
        uploader.start(serviceScope)

        createNotificationChannel()
        createAlertNotificationChannel()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service received action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_WAITING -> startWaiting()
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
            ACTION_SHUTDOWN -> shutdownService()
            else -> {
                // If service is killed and restarted, it comes here.
                // We want to resume in a waiting state.
                startWaiting()
            }
        }
        return START_STICKY
    }

    private fun startWaiting() {
        Log.d(TAG, "Entering waiting state")
        currentState = TrackingState.WAITING
        startForeground(NOTIF_ID, buildNotification("Tracking Paused", TrackingState.WAITING))
        maybeSuggestDisableBatteryOptimization()
    }

    private suspend fun tryStartSession() {
        if (trackingId != null) return
        if (isStartingSession.get()) return

        val now = System.currentTimeMillis()
        if (now - lastSessionStartAttempt < 10000) return

        if (!isStartingSession.compareAndSet(false, true)) return

        try {
            lastSessionStartAttempt = System.currentTimeMillis()
            Log.d(TAG, "Notifying repo about start")
            val id = repo.notifyStart()
            if (id != null) {
                trackingId = id
                prefs.edit { putString(KEY_TRACKING_ID, id) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
        } finally {
            isStartingSession.set(false)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startTracking() {
        if (currentState == TrackingState.TRACKING) {
            Log.d(TAG, "Already tracking, ignoring start request.")
            return
        }
        currentState = TrackingState.TRACKING
        Log.d(TAG, "Starting tracking...")

        // IMPORTANT: When started via startForegroundService(), we must call startForeground
        // within a few seconds or the system will throw ForegroundServiceDidNotStartInTimeException.
        startForeground(NOTIF_ID, buildNotification("Starting…", TrackingState.TRACKING))

        if (!PermissionsChecker.hasLocationPermission(this)) {
            postAlertNotification("Permission required. Tap to open app.")
            shutdownService()
            return
        }

        cancelAlertNotification()

        updateNotification("Starting tracking…", TrackingState.TRACKING)

        // Register acceleration sensor
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Resume prior tracking ID or start a new session
        trackingId = prefs.getString(KEY_TRACKING_ID, null)
        Log.d(TAG, "Old Tracking ID: $trackingId")

        if (trackingId == null) {
            Log.d(TAG, "Starting new session")
            serviceScope.launch(Dispatchers.IO) {
                tryStartSession()
            }
        }

        // Start location updates
        val request = LocationRequest.Builder(1000L)
            .setMinUpdateIntervalMillis(500L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                lastLocation = loc
                handleLocation(loc)
            }
        }
        fused.requestLocationUpdates(request, cb, mainLooper)

        locationJob?.cancel()
        locationJob = Job()
        locationJob?.invokeOnCompletion { fused.removeLocationUpdates(cb) }
    }

    private fun handleLocation(loc: Location) {
        val speedMps = loc.speed.toDouble()

        serviceScope.launch(Dispatchers.IO) {
            val accMag = accelMutex.withLock {
                accelValues?.let { v -> sqrt((v[0]*v[0] + v[1]*v[1] + v[2]*v[2]).toDouble()) }
            } ?: 0.0

            if (trackingId == null) {
                tryStartSession()
            }

            val id = trackingId
            if (id == null) {
                updateNotification("Waiting for session… v=${"%1$.1f".format(speedMps)} m/s a=${"%1$.2f".format(accMag)} m/s²", TrackingState.TRACKING)
                return@launch
            }

            val accMeters = if (loc.hasAccuracy()) loc.accuracy.toDouble() else Double.POSITIVE_INFINITY

            val hasGoodAccuracy = accMeters <= maxAccurancyMetters
            if (!hasGoodAccuracy) {
                updateNotification("Waiting for GPS accuracy… (${accMeters.toInt()} m)", TrackingState.TRACKING)
                return@launch
            }

            updateNotification("Tracking started", TrackingState.TRACKING)

            // Snapshot latest OBD metrics (updated independently at max BLE rate).
            val pidValues = ObdBleManager.pidValues.value
            val gpsSpeedMps = loc.takeIf { it.hasSpeed() }?.speed?.toDouble()
            val filtered = stationaryFilter.accept(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = accMeters,
                obdSpeedKph = pidValues["0d"],
                gpsSpeedMps = gpsSpeedMps,
            )

            GpsDataSource.updateLocation(
                LocationData(
                    latitude = filtered.latitude,
                    longitude = filtered.longitude,
                    accuracy = filtered.accuracy,
                )
            )

            val sample = Sample(
                trackingId = id,
                lat = filtered.latitude,
                lon = filtered.longitude,
                acc = filtered.accuracy,

                vehicleEngineRpm = pidValues["0c"],
                vehicleSpeedKph = pidValues["0d"],
                fuelConsumptionRate = pidValues["ff125a"],
                engineLoadPct = pidValues["04"],
                absoluteEngineLoadPct = pidValues["43"],
                shortTermFuelTrimPct = pidValues["06"],
                longTermFuelTrimPct = pidValues["07"],
                fuelLevelPct = FuelLevelReading.fromPidValues(pidValues),

                acceleratorPedalPct = pidValues["49"],
                ambientAirTempC = pidValues["46"],

                odometerValueKm = OdometerReading.fromPidValues(pidValues),
                engineCoolantTempC = pidValues["05"],
                manifoldAbsolutePressureKpa = pidValues["0b"],
                controlModuleVoltage = pidValues["42"],
                engineOnTime = pidValues["1f"],
                massAirFlow = pidValues["10"]
                    ?: pidValues[ObdBleManager.ESTIMATED_MAF_KEY],
                lambdaCmd = pidValues["44"],
                atmosphericPressure = pidValues["33"],
                intakeAirTemperature = pidValues["0f"],
            )
            // Local enqueue only — never block collection on network.
            runCatching { queueRepo.enqueue(id, sample) }
                .onFailure { Log.e(TAG, "Failed to enqueue sample", it) }

            updateNotification("Running: v=${"%1$.1f".format(speedMps)} m/s a=${"%1$.2f".format(accMag)} m/s²", TrackingState.TRACKING)
        }
    }

    private fun stopTracking() {
        if (currentState == TrackingState.WAITING) {
            Log.d(TAG, "Already in waiting state.")
            // Fix for UI desync: if service restarted but prefs still have tracking ID,
            // the UI thinks it is running. We must clear it.
            val lingeringId = prefs.getString(KEY_TRACKING_ID, null)
            if (lingeringId != null) {
                Log.w(TAG, "Found lingering tracking ID $lingeringId in prefs. Cleaning up.")
                prefs.edit { remove(KEY_TRACKING_ID) }
                serviceScope.launch(Dispatchers.IO) {
                    runCatching { uploader.flushNow() }
                    runCatching { repo.notifyStop(lingeringId) }
                }
            }
            return
        }
        Log.d(TAG, "Stopping tracking, returning to waiting state.")
        currentState = TrackingState.WAITING

        locationJob?.cancel()
        locationJob = null
        sensorManager.unregisterListener(this)

        val idToStop = trackingId
        prefs.edit { remove(KEY_TRACKING_ID) }
        trackingId = null

        serviceScope.launch(Dispatchers.IO) {
            // Best-effort flush before session stop; remainder stays queued for later.
            runCatching { uploader.flushNow() }
            if (idToStop != null) {
                runCatching { repo.notifyStop(idToStop) }
            }
        }

        updateNotification("Tracking Paused", TrackingState.WAITING)
    }

    private fun shutdownService() {
        Log.d(TAG, "Shutting down service.")
        stopTracking() // Clean up tracking resources + best-effort flush
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        sensorManager.unregisterListener(this)
        if (::uploader.isInitialized) {
            uploader.stop()
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {}
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Notification helpers
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Using IMPORTANCE_DEFAULT ensures the notification is not hidden by the system (e.g. collapsed).
            // We use setOnlyAlertOnce(true) in the builder to prevent constant noise on updates.
            val channel = NotificationChannel(CHANNEL_ID, "GPS Tracking", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ALERTS_CHANNEL_ID, "GPS Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Important alerts from GPSCarTracking"
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, state: TrackingState): Notification {
        val actionIntent: Intent
        val actionTitle: String
        val actionIcon: Int

        when (state) {
            TrackingState.TRACKING -> {
                actionIntent = Intent(this, ServiceControlReceiver::class.java).apply { action = ACTION_STOP }
                actionTitle = "Stop"
                actionIcon = android.R.drawable.ic_media_pause
            }
            TrackingState.WAITING -> {
                actionIntent = Intent(this, ServiceControlReceiver::class.java).apply { action = ACTION_SHUTDOWN }
                actionTitle = "Shutdown"
                actionIcon = android.R.drawable.ic_lock_power_off
            }
        }

        val pAction = PendingIntent.getBroadcast(
            this, 0, actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pOpen = PendingIntent.getActivity(
            this, 1, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GPSCarTracking")
            .setContentText(text)
            .setContentIntent(pOpen)
            .setOngoing(true) // Always ongoing
            .setOnlyAlertOnce(true) // Prevent noise on updates
            .addAction(actionIcon, actionTitle, pAction)

        // Android 12+: Ensure notification shows immediately and is persistent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun postAlertNotification(text: String) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pOpen = PendingIntent.getActivity(
            this, 1001, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (PendingIntent.FLAG_IMMUTABLE)
        )

        val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("GPSCarTracking")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pOpen)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_NOTIF_ID, notification)
    }

    private fun cancelAlertNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(ALERT_NOTIF_ID) }
    }

    private fun updateNotification(text: String, state: TrackingState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, state))
    }

    private fun maybeSuggestDisableBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val pkg = packageName
        val ignoring = pm.isIgnoringBatteryOptimizations(pkg)

        if (!ignoring) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData("package:$pkg".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        }
    }

    companion object {
        const val TAG = "TrackingService"
        const val CHANNEL_ID = "gps_tracking_channel"
        const val ALERTS_CHANNEL_ID = "gps_alerts_channel"
        const val NOTIF_ID = 42
        const val ALERT_NOTIF_ID = 43

        const val ACTION_START_WAITING = "com.domivega.gps_car.action.START_WAITING"
        const val ACTION_START = "com.domivega.gps_car.action.START_TRACKING"
        const val ACTION_STOP = "com.domivega.gps_car.action.STOP_TRACKING"
        const val ACTION_SHUTDOWN = "com.domivega.gps_car.action.SHUTDOWN"

        // Keys are public so other components can read current running state safely
        const val KEY_TRACKING_ID = "tracking_id"
    }
}

object PermissionsChecker {
    fun hasLocationPermission(ctx: Context): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
