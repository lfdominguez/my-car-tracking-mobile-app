package com.domivega.gps_car.data.queue

import android.content.Context
import android.util.Log
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.network.Sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Periodically drains [PendingSampleDao] via [ApiClient.sendSamples].
 *
 * Base interval is 60s; consecutive flush failures double the delay up to 10 minutes.
 * Success resets the delay. Leftover IN_FLIGHT rows are recovered on [start].
 */
class SampleQueueUploader(
    context: Context,
    private val api: ApiClient,
) {
    private val dao = TrackingDatabase.getInstance(context).pendingSampleDao()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val flushMutex = Mutex()
    private var job: Job? = null

    @Volatile
    private var delayMs: Long = BASE_DELAY_MS

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            runCatching { dao.resetInFlightToPending() }
                .onFailure { Log.e(TAG, "Failed to reset IN_FLIGHT rows", it) }

            while (isActive) {
                runCatching { flushOnce() }
                    .onFailure { Log.e(TAG, "Unexpected flush error", it) }
                delay(delayMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Best-effort immediate flush (e.g. on stop tracking). */
    suspend fun flushNow() = flushOnce()

    suspend fun flushOnce() = flushMutex.withLock {
        val batch = dao.getBatch(BATCH_SIZE)
        if (batch.isEmpty()) return@withLock

        val ids = batch.map { it.id }
        dao.markInFlight(ids)

        val samples = ArrayList<Sample>(batch.size)
        val entityByRecordedAt = HashMap<Long, MutableList<PendingSampleEntity>>(batch.size)
        val decodeFailedIds = ArrayList<Long>()

        for (entity in batch) {
            val sample = runCatching {
                json.decodeFromString(Sample.serializer(), entity.payloadJson)
            }.getOrElse { e ->
                Log.e(TAG, "Failed to decode sample id=${entity.id}", e)
                decodeFailedIds.add(entity.id)
                null
            } ?: continue
            samples.add(sample)
            entityByRecordedAt.getOrPut(sample.recordedAt) { mutableListOf() }.add(entity)
        }

        if (decodeFailedIds.isNotEmpty()) {
            dao.markFailed(decodeFailedIds, "decode_error")
        }

        if (samples.isEmpty()) {
            // All rows failed to decode; treat as a failed flush for backoff purposes.
            onFlushFailure()
            return@withLock
        }

        val result = api.sendSamples(samples)
        result.fold(
            onSuccess = { response ->
                val rejectedByRecordedAt = response.rejected.groupBy { it.recordedAt }
                val toDelete = ArrayList<Long>()
                val failedByError = LinkedHashMap<String, MutableList<Long>>()

                for ((recordedAt, entities) in entityByRecordedAt) {
                    val rejections = rejectedByRecordedAt[recordedAt].orEmpty()
                    if (rejections.isEmpty()) {
                        entities.forEach { toDelete.add(it.id) }
                        continue
                    }
                    // Match rejections to entities in order when multiple share recorded_at.
                    entities.forEachIndexed { index, entity ->
                        val rejection = rejections.getOrNull(index) ?: rejections.first()
                        if (isDuplicateRejection(rejection.reason)) {
                            toDelete.add(entity.id)
                        } else {
                            failedByError.getOrPut(rejection.reason) { mutableListOf() }.add(entity.id)
                        }
                    }
                }

                if (toDelete.isNotEmpty()) {
                    dao.deleteByIds(toDelete)
                }
                for ((error, failedIds) in failedByError) {
                    dao.markFailed(failedIds, error.take(MAX_ERROR_LEN))
                }

                onFlushSuccess()
                Log.d(
                    TAG,
                    "Flush ok: batch=${batch.size} deleted=${toDelete.size} " +
                        "rejected=${response.rejected.size} accepted=${response.accepted}"
                )
            },
            onFailure = { error ->
                val message = (error.message ?: error.toString()).take(MAX_ERROR_LEN)
                val inflightIds = batch.map { it.id }.filter { it !in decodeFailedIds }
                if (inflightIds.isNotEmpty()) {
                    dao.markFailed(inflightIds, message)
                }
                onFlushFailure()
                Log.w(TAG, "Flush HTTP/network failure: $message")
            }
        )
    }

    private fun onFlushSuccess() {
        delayMs = BASE_DELAY_MS
    }

    private fun onFlushFailure() {
        delayMs = (delayMs * 2).coerceAtMost(MAX_DELAY_MS)
    }

    private fun isDuplicateRejection(reason: String): Boolean {
        val r = reason.lowercase()
        return r.contains("duplicate") || r.contains("already")
    }

    companion object {
        private const val TAG = "SampleQueueUploader"
        private const val BATCH_SIZE = 200
        private const val BASE_DELAY_MS = 60_000L
        private const val MAX_DELAY_MS = 10 * 60_000L
        private const val MAX_ERROR_LEN = 500
    }
}
