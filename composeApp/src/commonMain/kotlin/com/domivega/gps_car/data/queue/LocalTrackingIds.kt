package com.domivega.gps_car.data.queue

/**
 * Client-side session ids used before `/start` returns a server tracking_id.
 * Prefixed ids are never sent to `/samples` until rewritten.
 */
object LocalTrackingIds {
    const val PREFIX = "local:"

    fun wrapLocal(rawUuid: String): String {
        val trimmed = rawUuid.trim()
        if (trimmed.startsWith(PREFIX)) return trimmed
        return PREFIX + trimmed
    }

    fun isLocal(id: String): Boolean = id.startsWith(PREFIX)

    fun isUploadable(id: String): Boolean = id.isNotBlank() && !isLocal(id)
}
