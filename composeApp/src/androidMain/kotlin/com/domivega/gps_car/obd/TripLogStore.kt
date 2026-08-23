package com.domivega.gps_car.obd

import android.content.Context
import android.util.Log
import com.domivega.gps_car.data.queue.TripLogEntity
import com.domivega.gps_car.data.queue.TrackingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TripLogStore {
    private const val TAG = "TripLogStore"
    private const val MAX_TRIPS = 5

    private val _tripLogs = MutableStateFlow<List<TripLogRecord>>(emptyList())
    val tripLogs: StateFlow<List<TripLogRecord>> = _tripLogs.asStateFlow()

    fun initialize(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val db = TrackingDatabase.getInstance(context)
                _tripLogs.value = db.tripLogDao().getLast(MAX_TRIPS).map { it.toRecord() }
            }.onFailure { Log.e(TAG, "Failed to load trip logs", it) }
        }
    }

    fun save(
        context: Context,
        scope: CoroutineScope,
        trackingId: String,
        startedAtMs: Long,
        endedAtMs: Long,
        entries: List<ObdLogEntry>,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val db = TrackingDatabase.getInstance(context)
                val text = formatObdLogAsText(entries)
                db.tripLogDao().insert(
                    TripLogEntity(
                        trackingId = trackingId,
                        startedAtMs = startedAtMs,
                        endedAtMs = endedAtMs,
                        logText = text,
                    )
                )
                db.tripLogDao().deleteOldExcept(MAX_TRIPS)
                _tripLogs.value = db.tripLogDao().getLast(MAX_TRIPS).map { it.toRecord() }
            }.onFailure { Log.e(TAG, "Failed to save trip log", it) }
        }
    }
}
