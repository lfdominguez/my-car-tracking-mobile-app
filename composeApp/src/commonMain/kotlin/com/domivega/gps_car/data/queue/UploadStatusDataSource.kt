package com.domivega.gps_car.data.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UploadStatus(
    val failedCount: Int = 0,
    val deadCount: Int = 0,
    val pendingUploadableCount: Int = 0,
    val lastFlushOk: Boolean? = null,
    val lastError: String? = null,
)

/**
 * Live queue/upload health for the dashboard banner.
 */
object UploadStatusDataSource {
    private val _status = MutableStateFlow(UploadStatus())
    val status: StateFlow<UploadStatus> = _status.asStateFlow()

    fun update(status: UploadStatus) {
        _status.value = status
    }

    private val _pauseReason = MutableStateFlow<UploadPauseReason?>(null)

    /**
     * Non-null while uploading is paused because the server refused this device
     * (token revoked) or the car (vault). Kept apart from [status] so queue-count
     * refreshes can never clear it; the persisted copy lives in Android prefs.
     */
    val pauseReason: StateFlow<UploadPauseReason?> = _pauseReason.asStateFlow()

    fun setPauseReason(reason: UploadPauseReason?) {
        _pauseReason.value = reason
    }
}
