package com.domivega.gps_car.data.queue

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.settings.AppSettings

/**
 * Background drain of the sample queue when the tracking service is not alive.
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
            uploader.refreshHealth()
            Result.success()
        } catch (e: Exception) {
            uploader.refreshHealth(lastFlushOk = false, lastError = e.message)
            Result.retry()
        }
    }
}
