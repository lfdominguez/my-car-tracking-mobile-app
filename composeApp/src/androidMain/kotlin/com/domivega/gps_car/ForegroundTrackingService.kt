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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import androidx.core.content.edit
import com.domivega.gps_car.data.queue.LocalTrackingIds
import com.domivega.gps_car.data.queue.SampleQueueRepository
import com.domivega.gps_car.data.queue.SampleQueueUploader
import com.domivega.gps_car.data.queue.SampleUploadScheduler
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.network.Sample
import com.domivega.gps_car.network.SampleFieldFilter
import com.domivega.gps_car.obd.EcuTrackingGate
import com.domivega.gps_car.obd.FuelLevelReading
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.OdometerReading
import com.domivega.gps_car.settings.AppSettings
import androidx.core.net.toUri
import com.domivega.gps_car.gps.GpsLocator
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
    private lateinit var gpsLocator: GpsLocator
    private lateinit var sensorManager: SensorManager
    private var accelValues: FloatArray? = null
    // Keep most recent GPS location to pair with acceleration-triggered events
    private var lastLocation: Location? = null
    private var trackingId: String? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var appSettings: AppSettings
    private lateinit var repo: TrackingRepository
    private lateinit var queueRepo: SampleQueueRepository
    private lateinit var uploader: SampleQueueUploader

    private val accelMutex = Mutex()
    private val isStartingSession = AtomicBoolean(false)
    private var lastSessionStartAttempt = 0L
    /** Bumped on Stop (and Start) so late GPS/bind coroutines cannot recreate a session. */
    private val collectionEpoch = AtomicLong(0L)
    /** Invalidates an in-flight shutdown flush so a later START does not get stopSelf(). */
    private val shutdownEpoch = AtomicLong(0L)
    private val shuttingDown = AtomicBoolean(false)

    private var currentState: TrackingState = TrackingState.WAITING

    // Tweakable thresholds (conservative defaults)
    private val maxAccurancyMetters = 15.0        // require reasonably accurate GNSS
    private val stationaryFilter = StationaryPositionFilter()


    override fun onCreate() {
        super.onCreate()

        gpsLocator = GpsLocator(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        appSettings = AppSettings(this)
        val api = ApiClient(appSettings)
        repo = TrackingRepository(api)
        queueRepo = SampleQueueRepository(this)
        uploader = SampleQueueUploader(this, api)
        // Start while service is alive (including WAITING) so leftover queue can drain.
        uploader.start(serviceScope)

        createNotificationChannel()
        createAlertNotificationChannel()

        // Retry any /stop that failed after a previous local stop.
        serviceScope.launch(Dispatchers.IO) {
            flushPendingStop()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service received action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_WAITING -> startWaiting()
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
            ACTION_SHUTDOWN -> {
                shutdownService()
                return START_NOT_STICKY
            }
            else -> {
                // Sticky restart (null intent) or unknown action:
                // resume TRACKING if a session id is still in prefs.
                if (prefs.getString(KEY_TRACKING_ID, null) != null) {
                    startTracking()
                } else {
                    startWaiting()
                    serviceScope.launch(Dispatchers.IO) { flushPendingStop() }
                }
            }
        }
        return START_STICKY
    }

    private fun startWaiting() {
        Log.d(TAG, "Entering waiting state")
        cancelPendingShutdown()
        currentState = TrackingState.WAITING
        startForeground(NOTIF_ID, buildNotification("Tracking Paused", TrackingState.WAITING))
        maybeSuggestDisableBatteryOptimization()
    }

    /**
     * Bind a local session id to a server tracking_id via `/start`, then rewrite queued samples.
     * Safe to call while already holding a `local:` id — does nothing once uploadable.
     * Never creates a new local id (Start owns that); aborts if Stop ran mid-flight.
     */
    private suspend fun tryBindServerSession(epochAtStart: Long = collectionEpoch.get()) {
        if (currentState != TrackingState.TRACKING) return
        if (epochAtStart != collectionEpoch.get()) return
        // Do not open a server trip until the ECU is actually online. GPS-only /
        // failed OBD idle reconnect must not create ghost tracks on the backend.
        if (!EcuTrackingGate.shouldBindServerSession(ObdBleManager.ecuConnected.value)) return

        val current = trackingId ?: prefs.getString(KEY_TRACKING_ID, null) ?: return
        if (LocalTrackingIds.isUploadable(current)) return
        if (isStartingSession.get()) return

        val now = System.currentTimeMillis()
        if (now - lastSessionStartAttempt < 10_000) return

        if (!isStartingSession.compareAndSet(false, true)) return

        try {
            if (currentState != TrackingState.TRACKING || epochAtStart != collectionEpoch.get()) return
            lastSessionStartAttempt = System.currentTimeMillis()
            val localId = trackingId ?: prefs.getString(KEY_TRACKING_ID, null) ?: return
            Log.d(TAG, "Binding server session for localId=$localId")
            val serverId = repo.notifyStart()
            // Re-check after network: Stop must not leave a fresh tracking_id in prefs.
            if (!TrackingCollectionGate.shouldCommitBoundSession(
                    epochAtStart = epochAtStart,
                    currentEpoch = collectionEpoch.get(),
                    isTracking = currentState == TrackingState.TRACKING,
                    serverId = serverId,
                    isUploadable = LocalTrackingIds::isUploadable,
                )
            ) {
                Log.d(TAG, "Discarding server bind after stop/epoch change (serverId=$serverId)")
                // /start already opened a remote session — end it so Stop does not leave orphans.
                if (serverId != null && LocalTrackingIds.isUploadable(serverId)) {
                    rememberPendingStop(serverId)
                    val stopRes = runCatching { repo.notifyStop(serverId) }
                        .getOrElse { Result.failure(it) }
                    stopRes
                        .onSuccess {
                            prefs.edit { remove(KEY_PENDING_STOP_ID) }
                        }
                        .onFailure {
                            Log.e(TAG, "Failed to stop discarded server session — queued pending stop", it)
                        }
                }
                return
            }
            val boundId = serverId!!
            runCatching { queueRepo.rewriteTrackingId(localId, boundId) }
                .onFailure { Log.e(TAG, "Failed to rewrite tracking ids", it) }
            trackingId = boundId
            prefs.edit { putString(KEY_TRACKING_ID, boundId) }
            SampleUploadScheduler.enqueue(this)
            Log.i(TAG, "Session bound local=$localId → server=$boundId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind server session", e)
        } finally {
            isStartingSession.set(false)
        }
    }

    /** Existing session only — GPS path must never mint a new id after Stop. */
    private fun currentSessionIdOrNull(): String? {
        val existing = trackingId ?: prefs.getString(KEY_TRACKING_ID, null)
        if (existing != null) {
            trackingId = existing
        }
        return existing
    }

    /** Create-or-return local session id. Call only from [startTracking]. */
    private fun ensureLocalSessionId(): String {
        currentSessionIdOrNull()?.let { return it }
        val local = LocalTrackingIds.wrapLocal(UUID.randomUUID().toString())
        trackingId = local
        prefs.edit { putString(KEY_TRACKING_ID, local) }
        Log.d(TAG, "Created local session id=$local")
        return local
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startTracking() {
        if (currentState == TrackingState.TRACKING) {
            Log.d(TAG, "Already tracking, ignoring start request.")
            return
        }
        cancelPendingShutdown()
        // New collection generation so work from a prior trip cannot resume.
        val epoch = collectionEpoch.incrementAndGet()
        currentState = TrackingState.TRACKING
        Log.d(TAG, "Starting tracking... epoch=$epoch")

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

        // Always have a session id (local until /start succeeds). Only Start may create it.
        ensureLocalSessionId()
        Log.d(TAG, "Tracking ID: $trackingId")

        serviceScope.launch(Dispatchers.IO) {
            tryBindServerSession(epochAtStart = epoch)
        }

        // Start location updates (platform fused on API 31+, else GPS)
        gpsLocator.start { loc ->
            lastLocation = loc
            handleLocation(loc)
        }
    }

    private fun handleLocation(loc: Location) {
        // Drop callbacks as soon as Stop/WAITING — do not schedule IO work.
        if (currentState != TrackingState.TRACKING) return

        val speedMps = loc.speed.toDouble()
        val epochAtStart = collectionEpoch.get()

        serviceScope.launch(Dispatchers.IO) {
            val sessionId = currentSessionIdOrNull()
            if (!TrackingCollectionGate.shouldCollect(
                    epochAtStart = epochAtStart,
                    currentEpoch = collectionEpoch.get(),
                    isTracking = currentState == TrackingState.TRACKING,
                    sessionId = sessionId,
                )
            ) {
                return@launch
            }
            val id = sessionId!!

            val accMag = accelMutex.withLock {
                accelValues?.let { v -> sqrt((v[0]*v[0] + v[1]*v[1] + v[2]*v[2]).toDouble()) }
            } ?: 0.0

            // Keep trying to bind server session in background (never mint a new local id here).
            if (!LocalTrackingIds.isUploadable(id)) {
                tryBindServerSession(epochAtStart = epochAtStart)
            }

            // Stop may have landed during bind attempt.
            if (!TrackingCollectionGate.shouldCollect(
                    epochAtStart = epochAtStart,
                    currentEpoch = collectionEpoch.get(),
                    isTracking = currentState == TrackingState.TRACKING,
                    sessionId = currentSessionIdOrNull(),
                )
            ) {
                return@launch
            }

            val accMeters = if (loc.hasAccuracy()) loc.accuracy.toDouble() else Double.POSITIVE_INFINITY

            val hasGoodAccuracy = accMeters <= maxAccurancyMetters
            if (!hasGoodAccuracy) {
                updateLiveTrackingNotification(
                    "Waiting for GPS accuracy… (${accMeters.toInt()} m)",
                    epochAtStart,
                )
                return@launch
            }

            updateLiveTrackingNotification("Tracking started", epochAtStart)

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

            // Final gate before enqueue — covers Stop during filtering above.
            val enqueueId = currentSessionIdOrNull()
            if (!TrackingCollectionGate.shouldCollect(
                    epochAtStart = epochAtStart,
                    currentEpoch = collectionEpoch.get(),
                    isTracking = currentState == TrackingState.TRACKING,
                    sessionId = enqueueId,
                )
            ) {
                return@launch
            }

            val sample = Sample(
                trackingId = enqueueId!!,
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
                // Prefer estimated when peak-air idle MAF was replaced (or no PID 0x10).
                massAirFlow = pidValues[ObdBleManager.ESTIMATED_MAF_KEY]
                    ?: pidValues["10"],
                lambdaCmd = pidValues["44"],
                atmosphericPressure = pidValues["33"],
                intakeAirTemperature = pidValues["0f"],
            )
            // Local enqueue only — never block collection on network.
            val toEnqueue = SampleFieldFilter.apply(sample, appSettings.sampleUploadFieldFlags())
            runCatching { queueRepo.enqueue(enqueueId, toEnqueue) }
                .onFailure { Log.e(TAG, "Failed to enqueue sample", it) }

            updateLiveTrackingNotification(
                "Running: v=${"%1$.1f".format(speedMps)} m/s a=${"%1$.2f".format(accMag)} m/s²",
                epochAtStart,
            )
        }
    }

    /**
     * @return server tracking id to notify on stop, if the session was bound.
     */
    private fun stopTrackingInternal(updateWaitingNotification: Boolean): String? {
        // Invalidate in-flight GPS/bind work before clearing session so they cannot recreate it.
        collectionEpoch.incrementAndGet()

        if (currentState == TrackingState.WAITING) {
            Log.d(TAG, "Already in waiting state.")
            val lingeringId = prefs.getString(KEY_TRACKING_ID, null)
            if (lingeringId != null) {
                Log.w(TAG, "Found lingering tracking ID $lingeringId in prefs. Cleaning up.")
                prefs.edit { remove(KEY_TRACKING_ID) }
                trackingId = null
                return lingeringId.takeIf { LocalTrackingIds.isUploadable(it) }
            }
            return null
        }
        Log.d(TAG, "Stopping tracking resources.")
        currentState = TrackingState.WAITING

        if (::gpsLocator.isInitialized) {
            gpsLocator.stop()
        }
        sensorManager.unregisterListener(this)

        val idToStop = trackingId?.takeIf { LocalTrackingIds.isUploadable(it) }
        prefs.edit { remove(KEY_TRACKING_ID) }
        trackingId = null

        if (updateWaitingNotification) {
            updateNotification("Tracking Paused", TrackingState.WAITING)
        }
        return idToStop
    }

    private fun stopTracking() {
        val idToStop = stopTrackingInternal(updateWaitingNotification = true)
        if (idToStop != null) {
            rememberPendingStop(idToStop)
        }
        serviceScope.launch(Dispatchers.IO) {
            runCatching { uploader.flushUntilEmpty() }
            flushPendingStop()
            SampleUploadScheduler.enqueue(this@ForegroundTrackingService)
        }
    }

    private fun shutdownService() {
        Log.d(TAG, "Shutting down service.")
        val myShutdown = shutdownEpoch.incrementAndGet()
        shuttingDown.set(true)
        val idToStop = stopTrackingInternal(updateWaitingNotification = false)
        if (idToStop != null) {
            rememberPendingStop(idToStop)
        }
        // Drop the "Running v=…" banner immediately — do not wait for leftover flush.
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Keep service alive until bounded flush completes, then stopSelf.
        serviceScope.launch(Dispatchers.IO) {
            try {
                runCatching { uploader.flushUntilEmpty() }
                flushPendingStop()
            } finally {
                SampleUploadScheduler.enqueue(this@ForegroundTrackingService)
                withContext(Dispatchers.Main) {
                    if (shutdownEpoch.get() == myShutdown && shuttingDown.get()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun cancelPendingShutdown() {
        shuttingDown.set(false)
        shutdownEpoch.incrementAndGet()
    }

    private fun rememberPendingStop(trackingId: String) {
        if (!LocalTrackingIds.isUploadable(trackingId)) return
        prefs.edit { putString(KEY_PENDING_STOP_ID, trackingId) }
        Log.i(TAG, "Queued pending stop for trackingId=$trackingId")
    }

    /**
     * Best-effort durable `/stop`. Clears [KEY_PENDING_STOP_ID] only on success
     * so offline stops retry on next service start / flush cycle.
     */
    private suspend fun flushPendingStop() {
        val pending = prefs.getString(KEY_PENDING_STOP_ID, null)
            ?: return
        if (!LocalTrackingIds.isUploadable(pending)) {
            prefs.edit { remove(KEY_PENDING_STOP_ID) }
            return
        }
        val result = runCatching { repo.notifyStop(pending) }.getOrElse { Result.failure(it) }
        result
            .onSuccess {
                prefs.edit { remove(KEY_PENDING_STOP_ID) }
                Log.i(TAG, "Pending stop delivered for trackingId=$pending")
            }
            .onFailure {
                Log.w(TAG, "Pending stop failed for trackingId=$pending — will retry", it)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gpsLocator.isInitialized) {
            gpsLocator.stop()
        }
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

    private fun updateLiveTrackingNotification(text: String, epochAtStart: Long) {
        if (!TrackingNotificationGate.shouldPublishLiveTrackingStatus(
                isTracking = currentState == TrackingState.TRACKING,
                shuttingDown = shuttingDown.get(),
                epochAtStart = epochAtStart,
                currentEpoch = collectionEpoch.get(),
            )
        ) {
            return
        }
        updateNotification(text, TrackingState.TRACKING)
    }

    private fun updateNotification(text: String, state: TrackingState) {
        if (shuttingDown.get() && state == TrackingState.TRACKING) return
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
        const val KEY_PENDING_STOP_ID = "pending_stop_tracking_id"
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
