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

    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SampleUploadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
