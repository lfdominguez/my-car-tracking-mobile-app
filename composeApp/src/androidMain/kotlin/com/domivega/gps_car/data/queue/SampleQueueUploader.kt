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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Periodically drains [PendingSampleDao] via [ApiClient.sendSamples].
 *
 * Base interval is 60s; consecutive flush failures double the delay up to 10 minutes.
 * Success resets the delay. Leftover IN_FLIGHT rows are recovered on [start].
 *
 * Rows still on a `local:` tracking id are never uploaded until rewritten after `/start`.
 * Transient network failures restore PENDING without burning attempts.
 */
class SampleQueueUploader(
    context: Context,
    private val api: ApiClient,
) {
    private val appContext = context.applicationContext
    private val dao = TrackingDatabase.getInstance(appContext).pendingSampleDao()
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
            runCatching { refreshHealth() }

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

    /**
     * Drain uploadable rows until empty, batch/time capped (shutdown / WorkManager).
     */
    suspend fun flushUntilEmpty(
        maxBatches: Int = MAX_DRAIN_BATCHES,
        maxDurationMs: Long = MAX_DRAIN_MS,
    ) {
        withTimeoutOrNull(maxDurationMs) {
            repeat(maxBatches) {
                val remaining = runCatching { dao.countUploadable() }.getOrDefault(0)
                if (remaining <= 0) return@withTimeoutOrNull
                flushOnce()
                val after = runCatching { dao.countUploadable() }.getOrDefault(0)
                if (after >= remaining) {
                    // No progress (likely offline) — stop draining.
                    return@withTimeoutOrNull
                }
            }
        }
        refreshHealth()
    }

    suspend fun flushOnce() = flushMutex.withLock {
        val batch = dao.getBatch(BATCH_SIZE)
        if (batch.isEmpty()) {
            refreshHealth(lastFlushOk = UploadStatusDataSource.status.value.lastFlushOk)
            return@withLock
        }

        val readyRows = batch.filter { LocalTrackingIds.isUploadable(it.trackingId) }
        if (readyRows.isEmpty()) {
            // Only unbound local-session rows — wait for /start rewrite.
            refreshHealth()
            return@withLock
        }

        val ids = readyRows.map { it.id }
        dao.markInFlight(ids)

        val samples = ArrayList<Sample>(readyRows.size)
        val entityByRecordedAt = HashMap<Long, MutableList<PendingSampleEntity>>(readyRows.size)
        val decodeFailedIds = ArrayList<Long>()

        for (entity in readyRows) {
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
            onFlushFailure("decode_error")
            refreshHealth(lastFlushOk = false, lastError = "decode_error")
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
                val err = failedByError.keys.firstOrNull()
                refreshHealth(lastFlushOk = failedByError.isEmpty(), lastError = err)
                Log.d(
                    TAG,
                    "Flush ok: batch=${readyRows.size} deleted=${toDelete.size} " +
                        "rejected=${response.rejected.size} accepted=${response.accepted}",
                )
            },
            onFailure = { error ->
                val message = (error.message ?: error.toString()).take(MAX_ERROR_LEN)
                val inflightIds = readyRows.map { it.id }.filter { it !in decodeFailedIds }
                if (inflightIds.isNotEmpty()) {
                    when (UploadFailureClassifier.classify(message)) {
                        UploadFailureKind.Transient -> {
                            dao.restoreToPending(inflightIds)
                            Log.w(TAG, "Flush transient failure (no attempt burn): $message")
                        }
                        UploadFailureKind.Permanent -> {
                            dao.markFailed(inflightIds, message)
                            Log.w(TAG, "Flush permanent failure: $message")
                        }
                    }
                }
                onFlushFailure(message)
                refreshHealth(lastFlushOk = false, lastError = message)
                SampleUploadScheduler.enqueue(appContext)
            },
        )
    }

    suspend fun refreshHealth(
        lastFlushOk: Boolean? = UploadStatusDataSource.status.value.lastFlushOk,
        lastError: String? = UploadStatusDataSource.status.value.lastError,
    ) {
        val failed = runCatching { dao.countByStatus(PendingSampleStatus.FAILED) }.getOrDefault(0)
        val dead = runCatching { dao.countByStatus(PendingSampleStatus.DEAD) }.getOrDefault(0)
        val uploadable = runCatching { dao.countUploadable() }.getOrDefault(0)
        UploadStatusDataSource.update(
            UploadStatus(
                failedCount = failed,
                deadCount = dead,
                pendingUploadableCount = uploadable,
                lastFlushOk = lastFlushOk,
                lastError = lastError,
            ),
        )
    }

    private fun onFlushSuccess() {
        delayMs = BASE_DELAY_MS
    }

    private fun onFlushFailure(message: String) {
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
        private const val MAX_DRAIN_BATCHES = 50
        private const val MAX_DRAIN_MS = 30_000L
    }
}
