package com.domivega.gps_car.obd

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-facing OBD debug log: ring buffer + StateFlow + Logcat mirror.
 */
object ObdDebugLog {
    private const val TAG = "ObdBleMgr"
    private const val CAPACITY = 2000
    private const val MAX_MSG = 400

    private val buffer = ObdLogBuffer(CAPACITY)
    private val _entries = MutableStateFlow<List<ObdLogEntry>>(emptyList())
    val entries: StateFlow<List<ObdLogEntry>> = _entries.asStateFlow()

    fun info(message: String) = append(ObdLogLevel.INFO, message)

    fun warn(message: String) = append(ObdLogLevel.WARN, message)

    fun error(message: String) = append(ObdLogLevel.ERROR, message)

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    private fun append(level: ObdLogLevel, message: String) {
        val msg = message.replace('\n', ' ').trim().take(MAX_MSG)
        buffer.append(level, msg, System.currentTimeMillis())
        _entries.value = buffer.snapshot()
        when (level) {
            ObdLogLevel.INFO -> Log.i(TAG, msg)
            ObdLogLevel.WARN -> Log.w(TAG, msg)
            ObdLogLevel.ERROR -> Log.e(TAG, msg)
        }
    }
}
