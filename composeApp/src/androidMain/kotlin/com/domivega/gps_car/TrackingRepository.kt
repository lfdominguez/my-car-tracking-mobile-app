package com.domivega.gps_car

import com.domivega.gps_car.network.ApiClient

/**
 * Session lifecycle against the tracking API.
 * Sample upload is handled by [com.domivega.gps_car.data.queue.SampleQueueUploader].
 */
class TrackingRepository(private val api: ApiClient) {

    /** Server tracking id, or the failure (so callers can spot a revoked token). */
    suspend fun notifyStart(): Result<String> {
        return api.startSession().map { it.id }
    }

    /** Returns Result so callers can persist pending stop on failure. */
    suspend fun notifyStop(trackingId: String): Result<Unit> {
        return api.stopSession(trackingId)
    }
}
