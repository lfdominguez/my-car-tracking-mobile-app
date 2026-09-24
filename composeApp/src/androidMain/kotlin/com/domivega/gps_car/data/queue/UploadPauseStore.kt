package com.domivega.gps_car.data.queue

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.domivega.gps_car.ForegroundTrackingService
import com.domivega.gps_car.MainActivity

/**
 * Persisted "uploading is paused" flag (runtime `tracking_prefs`).
 *
 * Set when the server refuses the device token (401/403 on any ingest call) or the
 * car (409 vault on `/samples`). While set, the uploader leaves the queue intact —
 * no attempts burned, no FAILED/DEAD rows — and `/start` / pending `/stop` wait.
 * Cleared when a new token is saved or Test connection returns OK.
 */
object UploadPauseStore {
    private const val TAG = "UploadPauseStore"
    private const val PREFS_NAME = "tracking_prefs"
    const val KEY_UPLOAD_PAUSE_REASON = "upload_pause_reason"
    private const val PAUSE_NOTIF_ID = 44

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): UploadPauseReason? =
        UploadPauseReason.fromName(prefs(context).getString(KEY_UPLOAD_PAUSE_REASON, null))

    fun isPaused(context: Context): Boolean = get(context) != null

    /** Publishes the persisted flag to the dashboard (process start). */
    fun publish(context: Context) {
        UploadStatusDataSource.setPauseReason(get(context))
    }

    /**
     * Persists [reason]. Posts one alert only when the reason changes, so a queue
     * that keeps hitting the same 401 does not re-notify every flush.
     * @return true when the flag was newly set (or changed reason)
     */
    fun pause(context: Context, reason: UploadPauseReason): Boolean {
        val previous = get(context)
        UploadStatusDataSource.setPauseReason(reason)
        if (previous == reason) return false
        prefs(context).edit().putString(KEY_UPLOAD_PAUSE_REASON, reason.name).apply()
        Log.w(TAG, "Uploading paused: $reason")
        postAlert(context, reason)
        return true
    }

    /** @return true when a pause flag was actually cleared */
    fun clear(context: Context): Boolean {
        val previous = get(context)
        UploadStatusDataSource.setPauseReason(null)
        if (previous == null) return false
        prefs(context).edit().remove(KEY_UPLOAD_PAUSE_REASON).apply()
        cancelAlert(context)
        Log.i(TAG, "Uploading resumed (was $previous)")
        return true
    }

    /** Clears the flag and resumes the drain (new token saved / Test connection OK). */
    fun clearAndResume(context: Context) {
        if (clear(context)) {
            SampleUploadScheduler.enqueueNow(context.applicationContext)
        }
    }

    /** Same channel and style as the service's "GPS Alerts" notifications. */
    private fun postAlert(context: Context, reason: UploadPauseReason) {
        runCatching {
            val app = context.applicationContext
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Idempotent; the service normally created it already.
                val channel = NotificationChannel(
                    ForegroundTrackingService.ALERTS_CHANNEL_ID,
                    "GPS Alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Important alerts from GPSCarTracking"
                }
                nm.createNotificationChannel(channel)
            }
            val pOpen = PendingIntent.getActivity(
                app,
                1002,
                Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = QueueHealthMessages.pauseMessage(reason).orEmpty()
            val notification = NotificationCompat.Builder(app, ForegroundTrackingService.ALERTS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("GPSCarTracking: uploads paused")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pOpen)
                .build()
            nm.notify(PAUSE_NOTIF_ID, notification)
        }.onFailure { Log.w(TAG, "Failed to post upload-paused alert", it) }
    }

    private fun cancelAlert(context: Context) {
        runCatching {
            val nm = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(PAUSE_NOTIF_ID)
        }
    }
}
