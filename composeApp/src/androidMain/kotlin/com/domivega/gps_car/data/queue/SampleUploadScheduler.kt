package com.domivega.gps_car.data.queue

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules a one-shot WorkManager drain of pending samples.
 */
object SampleUploadScheduler {
    private const val UNIQUE_WORK = "sample_upload_drain"
    private const val UNIQUE_WORK_AFTER_EDIT = "sample_upload_after_settings_edit"
    private const val SETTINGS_EDIT_SETTLE_SECONDS = 10L

    /** Background / failure path: do not replace an already-scheduled drain. */
    fun enqueue(context: Context) {
        enqueueInternal(context, ExistingWorkPolicy.KEEP)
    }

    /** Manual Retry: always (re)schedule so work runs even if a prior job is sitting idle. */
    fun enqueueNow(context: Context) {
        enqueueInternal(context, ExistingWorkPolicy.REPLACE)
    }

    /**
     * After a settings edit (e.g. the token field, which saves per keystroke): one
     * drain once typing has paused. Each call pushes it back, and it has its own
     * work name so it never cancels a drain already running.
     */
    fun enqueueAfterSettingsEdit(context: Context) {
        enqueueInternal(
            context,
            ExistingWorkPolicy.REPLACE,
            uniqueName = UNIQUE_WORK_AFTER_EDIT,
            delaySeconds = SETTINGS_EDIT_SETTLE_SECONDS,
        )
    }

    private fun enqueueInternal(
        context: Context,
        policy: ExistingWorkPolicy,
        uniqueName: String = UNIQUE_WORK,
        delaySeconds: Long = 0,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SampleUploadWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName, policy, request)
    }
}
