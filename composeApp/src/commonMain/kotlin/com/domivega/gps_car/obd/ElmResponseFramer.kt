package com.domivega.gps_car.obd

/**
 * Accumulates ELM ASCII chunks until the `>` prompt marks a complete response.
 * Thread-safety is the caller's responsibility (same as prior synchronized buffer).
 */
class ElmResponseFramer {
    private val buffer = StringBuilder()

    fun append(chunk: String): String? {
        buffer.append(chunk)
        val current = buffer.toString()
        if (!current.contains('>')) return null
        buffer.setLength(0)
        return current
    }

    fun drainPartial(): String {
        val partial = buffer.toString()
        buffer.setLength(0)
        return partial
    }

    fun clear() {
        buffer.setLength(0)
    }

    fun snapshot(): String = buffer.toString()
}
