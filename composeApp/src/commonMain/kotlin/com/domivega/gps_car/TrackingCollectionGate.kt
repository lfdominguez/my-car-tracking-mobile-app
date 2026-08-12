package com.domivega.gps_car

/**
 * Pure guards for GPS sample collection / session bind after Stop.
 *
 * Stop must end *new* collection while still allowing backlog upload.
 * In-flight location coroutines capture [epochAtStart]; Stop bumps the live epoch
 * so late work cannot recreate a session or enqueue samples.
 */
object TrackingCollectionGate {
    /**
     * Whether a location coroutine may continue collecting / binding.
     *
     * @param epochAtStart generation captured when the coroutine was scheduled
     * @param currentEpoch live generation (bumped on Stop / before Start)
     * @param isTracking service is in TRACKING (not WAITING)
     * @param sessionId existing session id only — never create one on the collect path
     */
    fun shouldCollect(
        epochAtStart: Long,
        currentEpoch: Long,
        isTracking: Boolean,
        sessionId: String?,
    ): Boolean {
        if (!isTracking) return false
        if (epochAtStart != currentEpoch) return false
        if (sessionId.isNullOrBlank()) return false
        return true
    }

    /**
     * Whether `/start` bind may write a server id into prefs after the network call.
     * Re-check after suspend so Stop during `/start` does not resurrect tracking_id.
     */
    fun shouldCommitBoundSession(
        epochAtStart: Long,
        currentEpoch: Long,
        isTracking: Boolean,
        serverId: String?,
        isUploadable: (String) -> Boolean,
    ): Boolean {
        if (!isTracking) return false
        if (epochAtStart != currentEpoch) return false
        val id = serverId ?: return false
        return isUploadable(id)
    }
}
