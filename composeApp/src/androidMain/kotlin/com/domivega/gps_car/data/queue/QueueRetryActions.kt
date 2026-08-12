package com.domivega.gps_car.data.queue

import android.content.Context
import android.util.Log
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual recovery for stuck sample rows (DEAD / FAILED / IN_FLIGHT).
 */
object QueueRetryActions {
    private const val TAG = "QueueRetryActions"
    private val drainScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Requeue stuck samples, refresh dashboard health, and kick upload (WorkManager + background drain).
     * Returns as soon as rows are reset so the UI can toast immediately.
     * @return number of rows reset to PENDING
     */
    suspend fun retryStuck(context: Context): Int = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val repo = SampleQueueRepository(appContext)
        val n = runCatching { repo.requeueStuckSamples() }
            .onFailure { Log.e(TAG, "requeueStuck failed", it) }
            .getOrDefault(0)

        // Clear permanent-failure banner state; upload will update lastFlushOk next.
        runCatching {
            repo.publishQueueHealth(lastFlushOk = null, lastError = null)
        }.onFailure { Log.e(TAG, "publishQueueHealth failed", it) }

        SampleUploadScheduler.enqueueNow(appContext)

        // Background drain so large backlogs (1k+) do not block the Retry toast.
        drainScope.launch {
            runCatching {
                val uploader = SampleQueueUploader(appContext, ApiClient(AppSettings(appContext)))
                uploader.flushUntilEmpty()
                uploader.refreshHealth()
            }.onFailure { Log.e(TAG, "immediate drain failed", it) }
        }

        n
    }
}
