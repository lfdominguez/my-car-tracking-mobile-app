package com.domivega.gps_car.data.queue

/**
 * User-facing dashboard copy for queue / upload problems.
 */
object QueueHealthMessages {
    fun warning(failedCount: Int, deadCount: Int, lastFlushOk: Boolean?): String? {
        if (deadCount > 0) {
            return "Upload failed permanently for $deadCount sample${suffix(deadCount)}"
        }
        if (failedCount > 0) {
            return "Sample upload failing ($failedCount) — will retry"
        }
        if (lastFlushOk == false) {
            return "Sample upload failing — will retry"
        }
        return null
    }

    private fun suffix(n: Int): String = if (n == 1) "" else "s"
}
