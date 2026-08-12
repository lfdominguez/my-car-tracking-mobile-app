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

    private fun suffix(n: Int): String = if (n == 1) "" else "s"
}
