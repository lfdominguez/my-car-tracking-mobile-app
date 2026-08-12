package com.domivega.gps_car.data.queue

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Schedules a one-shot WorkManager drain of pending samples.
 */
object SampleUploadScheduler {
    private const val UNIQUE_WORK = "sample_upload_drain"

    /** Background / failure path: do not replace an already-scheduled drain. */
    fun enqueue(context: Context) {
        enqueueInternal(context, ExistingWorkPolicy.KEEP)
    }

    /** Manual Retry: always (re)schedule so work runs even if a prior job is sitting idle. */
    fun enqueueNow(context: Context) {
        enqueueInternal(context, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueInternal(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SampleUploadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }
}
