package com.domivega.gps_car.data.queue

/**
 * User-facing dashboard copy for queue / upload problems.
 */
object QueueHealthMessages {
    fun warning(
        failedCount: Int,
        deadCount: Int,
        lastFlushOk: Boolean?,
        lastError: String? = null,
    ): String? {
        val detail = lastError?.trim()?.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        if (deadCount > 0) {
            return "Upload failed permanently for $deadCount sample${suffix(deadCount)}$detail — tap Retry"
        }
        if (failedCount > 0) {
            return "Sample upload failing ($failedCount)$detail — will retry"
        }
        if (lastFlushOk == false) {
            return "Sample upload failing$detail — will retry"
        }
        return null
    }

    /** Dashboard banner while uploading is paused; takes precedence over [warning]. */
    fun pauseMessage(reason: UploadPauseReason?): String? = when (reason) {
        null -> null
        UploadPauseReason.DeviceUnauthorized ->
            "This phone was unlinked from the car on the server. Scan a new QR in Settings. " +
                "Recorded samples are kept and upload once a new token is saved."
        UploadPauseReason.VaultRequired ->
            "This car requires end-to-end vault uploads, which this app doesn't support. " +
                "Uploading is paused; recorded samples are kept on the phone."
    }

    private fun suffix(n: Int): String = if (n == 1) "" else "s"
}
