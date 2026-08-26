package com.domivega.gps_car.obd

/**
 * Thread-safe ring buffer for OBD debug lines (newest at end).
 * Pure logic — no Android deps; platform code supplies timestamps.
 *
 * 2000 entries ≈ a full drive. At 300 the 10s throughput line alone evicted every
 * PID miss within ~50 minutes, so shared logs carried no diagnostic signal.
 */
class ObdLogBuffer(capacity: Int = 2000) {
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
