package com.domivega.gps_car.data.queue

import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.msg_upload_dead
import com.domivega.gps_car.generated.resources.msg_upload_failing
import com.domivega.gps_car.generated.resources.msg_upload_failing_count
import com.domivega.gps_car.generated.resources.msg_upload_unlinked
import com.domivega.gps_car.ui.UiText

/**
 * User-facing dashboard copy for queue / upload problems.
 */
object QueueHealthMessages {
    fun warning(
        failedCount: Int,
        deadCount: Int,
        lastFlushOk: Boolean?,
        lastError: String? = null,
    ): UiText? {
        val detail = lastError?.trim()?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        if (deadCount > 0) {
            return UiText.Plural(Res.plurals.msg_upload_dead, deadCount, listOf(deadCount, detail))
        }
        if (failedCount > 0) {
            return UiText.Res(Res.string.msg_upload_failing_count, listOf(failedCount, detail))
        }
        if (lastFlushOk == false) {
            return UiText.Res(Res.string.msg_upload_failing, listOf(detail))
        }
        return null
    }

    /** Dashboard banner while uploading is paused; takes precedence over [warning]. */
    fun pauseMessage(reason: UploadPauseReason?): UiText? = when (reason) {
        null -> null
        UploadPauseReason.DeviceUnauthorized -> UiText.Res(Res.string.msg_upload_unlinked)
    }
}
