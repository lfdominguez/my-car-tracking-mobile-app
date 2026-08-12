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
}
