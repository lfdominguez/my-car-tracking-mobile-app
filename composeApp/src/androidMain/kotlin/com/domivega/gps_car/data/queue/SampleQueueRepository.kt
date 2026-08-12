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

    /**
     * After `/start` succeeds, rewrite all rows still keyed by the local session id.
     */
    suspend fun rewriteTrackingId(oldId: String, newId: String) {
        if (oldId == newId || oldId.isBlank() || newId.isBlank()) return
        val rows = dao.getByTrackingId(oldId)
        for (row in rows) {
            val newJson = SamplePayloadRewrite.replaceTrackingId(row.payloadJson, newId)
            dao.updatePayloadAndTrackingId(row.id, newId, newJson)
        }
    }

    suspend fun countPending(): Int = dao.countPending()

    suspend fun countFailed(): Int = dao.countByStatus(PendingSampleStatus.FAILED)

    suspend fun countDead(): Int = dao.countByStatus(PendingSampleStatus.DEAD)

    suspend fun countUploadable(): Int = dao.countUploadable()

    /**
     * Reset DEAD/FAILED/IN_FLIGHT rows so the uploader will try them again.
     * @return number of rows requeued
     */
    suspend fun requeueStuckSamples(): Int = dao.requeueStuck()

    suspend fun publishQueueHealth(
        lastFlushOk: Boolean? = UploadStatusDataSource.status.value.lastFlushOk,
        lastError: String? = null,
    ) {
        UploadStatusDataSource.update(
            UploadStatus(
                failedCount = countFailed(),
                deadCount = countDead(),
                pendingUploadableCount = countUploadable(),
                lastFlushOk = lastFlushOk,
                lastError = lastError,
            ),
        )
    }
}
