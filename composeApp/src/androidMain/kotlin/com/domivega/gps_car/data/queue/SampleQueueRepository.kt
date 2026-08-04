package com.domivega.gps_car.data.queue

import android.content.Context
import com.domivega.gps_car.network.Sample
import kotlinx.serialization.json.Json

/**
 * Durable local queue for samples waiting to be uploaded.
 */
class SampleQueueRepository(context: Context) {
    private val dao = TrackingDatabase.getInstance(context).pendingSampleDao()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun enqueue(trackingId: String, sample: Sample) {
        val now = System.currentTimeMillis()
        val entity = PendingSampleEntity(
            trackingId = trackingId,
            recordedAt = sample.recordedAt,
            payloadJson = json.encodeToString(Sample.serializer(), sample),
            status = PendingSampleStatus.PENDING,
            attempts = 0,
            lastError = null,
            createdAt = now,
        )
        dao.insert(entity)
    }

    suspend fun countPending(): Int = dao.countPending()
}
