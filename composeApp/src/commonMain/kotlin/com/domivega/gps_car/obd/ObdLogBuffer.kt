package com.domivega.gps_car.obd

/**
 * Thread-safe ring buffer for OBD debug lines (newest at end).
 * Pure logic — no Android deps; platform code supplies timestamps.
 */
class ObdLogBuffer(capacity: Int = 300) {
    private val capacity = capacity.coerceAtLeast(1)
    private val lock = Any()
    private val entries = ArrayDeque<ObdLogEntry>(this.capacity)

    fun append(level: ObdLogLevel, message: String, nowMs: Long): ObdLogEntry {
        val entry = ObdLogEntry(timestampMs = nowMs, level = level, message = message)
        synchronized(lock) {
            while (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
        return entry
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    fun snapshot(): List<ObdLogEntry> = synchronized(lock) {
        entries.toList()
    }
}
