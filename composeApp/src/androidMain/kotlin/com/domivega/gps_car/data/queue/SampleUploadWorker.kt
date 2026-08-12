package com.domivega.gps_car.data.queue

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.domivega.gps_car.ForegroundTrackingService
import com.domivega.gps_car.TrackingRepository
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.settings.AppSettings

/**
 * Background drain of the sample queue when the tracking service is not alive.
 * Also retries a durable pending `/stop` if the last local stop never reached the server.
 */
class SampleUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val api = ApiClient(AppSettings(applicationContext))
        val uploader = SampleQueueUploader(applicationContext, api)
        return try {
            uploader.flushUntilEmpty()
            flushPendingStop(api)
            uploader.refreshHealth()
            Result.success()
        } catch (e: Exception) {
            uploader.refreshHealth(lastFlushOk = false, lastError = e.message)
            Result.retry()
        }
    }

    private suspend fun flushPendingStop(api: ApiClient) {
        val prefs = applicationContext.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        val pending = prefs.getString(ForegroundTrackingService.KEY_PENDING_STOP_ID, null) ?: return
        if (!LocalTrackingIds.isUploadable(pending)) {
            prefs.edit().remove(ForegroundTrackingService.KEY_PENDING_STOP_ID).apply()
            return
        }
        val result = TrackingRepository(api).notifyStop(pending)
        if (result.isSuccess) {
            prefs.edit().remove(ForegroundTrackingService.KEY_PENDING_STOP_ID).apply()
            Log.i(TAG, "WorkManager delivered pending stop for $pending")
        } else {
            Log.w(TAG, "WorkManager pending stop failed for $pending: ${result.exceptionOrNull()?.message}")
        }
    }

    companion object {
        private const val TAG = "SampleUploadWorker"
    }
}
