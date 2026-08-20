package com.domivega.gps_car.obd

/**
 * Distinguishes a dead ELM write (adapter unplugged / 12 V cut) from a PID timeout.
 * Write failures must abort the poll round; otherwise every remaining PID logs
 * "Write failed for …" in a tight loop.
 */
object ElmCommandFailure {
    fun isFatalWrite(message: String?): Boolean {
        val m = message ?: return false
        return m.contains("Write failed", ignoreCase = true)
    }

    fun shouldAbortPoll(
        isLinked: Boolean,
        writeFailed: Boolean,
    ): Boolean = !isLinked || writeFailed
}
