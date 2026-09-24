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
import com.domivega.gps_car.settings.AppSettings

/**
 * Persisted "uploading is paused" flag (runtime `tracking_prefs`).
 *
 * Set when the server refuses the device token (401/403 on any ingest call) or the
 * car (409 vault on `/samples`). While set, the uploader leaves the queue intact —
 * no attempts burned, no FAILED/DEAD rows — and `/start` / pending `/stop` wait.
 * The pause belongs to the token the server refused ([PauseTokenBinding]): it holds
 * only while that token is configured, so editing the token lifts it and a late 401
 * for a replaced token cannot re-pause. Test connection OK also clears it.
 */
object UploadPauseStore {
    private const val TAG = "UploadPauseStore"
    private const val PREFS_NAME = "tracking_prefs"
    const val KEY_UPLOAD_PAUSE_REASON = "upload_pause_reason"
    private const val KEY_UPLOAD_PAUSE_TOKEN_FP = "upload_pause_token_fp"
    private const val PAUSE_NOTIF_ID = 44

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun currentToken(context: Context): String = AppSettings(context.applicationContext).apiToken

    /** The active pause, or null when none is stored or it was for a token since replaced. */
    fun get(context: Context): UploadPauseReason? {
        val p = prefs(context)
        val reason = UploadPauseReason.fromName(p.getString(KEY_UPLOAD_PAUSE_REASON, null)) ?: return null
        val fp = p.getString(KEY_UPLOAD_PAUSE_TOKEN_FP, null)
        return if (PauseTokenBinding.isActive(fp, currentToken(context))) reason else null
    }

    fun isPaused(context: Context): Boolean = get(context) != null

    /** Publishes the persisted flag to the dashboard (process start). */
    fun publish(context: Context) {
        UploadStatusDataSource.setPauseReason(get(context))
    }

    /**
     * Persists [reason] for [tokenUsed], the token the refused request carried. A
     * refusal of a token that is no longer configured is ignored (the caller should
     * retry with the new one). Posts one alert only when the reason changes, so a
     * queue that keeps hitting the same 401 does not re-notify every flush.
     * @return true when the flag was newly set (or changed reason)
     */
    fun pause(context: Context, reason: UploadPauseReason, tokenUsed: String): Boolean {
        if (PauseTokenBinding.isStaleRefusal(tokenUsed, currentToken(context))) {
            Log.i(TAG, "Ignoring $reason for a token that has since been replaced")
            return false
        }
        val previous = get(context)
        UploadStatusDataSource.setPauseReason(reason)
        if (previous == reason) return false
        prefs(context).edit()
            .putString(KEY_UPLOAD_PAUSE_REASON, reason.name)
            .putString(KEY_UPLOAD_PAUSE_TOKEN_FP, PauseTokenBinding.fingerprint(tokenUsed))
            .apply()
        Log.w(TAG, "Uploading paused: $reason")
        postAlert(context, reason)
        return true
    }

    /** @return true when a pause flag was actually cleared */
    fun clear(context: Context): Boolean {
        val previous = get(context)
        UploadStatusDataSource.setPauseReason(null)
        if (previous == null) return false
        prefs(context).edit()
            .remove(KEY_UPLOAD_PAUSE_REASON)
            .remove(KEY_UPLOAD_PAUSE_TOKEN_FP)
            .apply()
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

    /**
     * The token setting changed (QR scan or a keystroke in the token field). A pause
     * for the refused token no longer applies, so drop its banner and alert and
     * schedule one drain after typing settles instead of on every keystroke.
     */
    fun onTokenChanged(context: Context) {
        val p = prefs(context)
        val stored = p.getString(KEY_UPLOAD_PAUSE_REASON, null) ?: return
        if (get(context) != null) {
            // The refused token was typed back in: still paused.
            publish(context)
            return
        }
        p.edit().remove(KEY_UPLOAD_PAUSE_REASON).remove(KEY_UPLOAD_PAUSE_TOKEN_FP).apply()
        UploadStatusDataSource.setPauseReason(null)
        cancelAlert(context)
        Log.i(TAG, "Token changed; lifting $stored pause")
        SampleUploadScheduler.enqueueAfterSettingsEdit(context.applicationContext)
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
