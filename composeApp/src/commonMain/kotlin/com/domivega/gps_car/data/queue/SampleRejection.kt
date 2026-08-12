package com.domivega.gps_car.data.queue

/**
 * Classification of per-sample rejection reasons from POST /api/track/samples.
 */
object SampleRejection {
    fun isDuplicate(reason: String): Boolean {
        val r = reason.lowercase()
        return r.contains("duplicate") || r.contains("already")
    }

    /**
     * Reasons that will never succeed on retry — drop the local row.
     *
     * Note: do **not** drop `track_finished` here. Older servers reject late
     * samples for finished trips; after server late-accept, Retry can drain them.
     * Out-of-window finished rejections still surface via lastError / FAILED.
     */
    fun isTerminal(reason: String): Boolean {
        if (isDuplicate(reason)) return true
        val r = reason.lowercase()
        return r.contains("unknown_track")
            || r.contains("unknown tracking")
            || r == "unknown"
            || r.contains("invalid_coords")
            || r.contains("invalid lat")
            || r.contains("invalid lon")
    }
}
