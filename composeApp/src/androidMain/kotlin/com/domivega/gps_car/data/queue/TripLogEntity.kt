package com.domivega.gps_car.data.queue

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.domivega.gps_car.obd.TripLogRecord

@Entity(tableName = "trip_logs")
data class TripLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tracking_id") val trackingId: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long,
    @ColumnInfo(name = "log_text") val logText: String,
) {
    fun toRecord() = TripLogRecord(
        id = id,
        trackingId = trackingId,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        logText = logText,
    )
}
