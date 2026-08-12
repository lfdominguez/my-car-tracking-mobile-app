package com.domivega.gps_car.data.queue

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object PendingSampleStatus {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val FAILED = "FAILED"
    const val DEAD = "DEAD"

    /** Attempts at or above this threshold mark a row as DEAD. */
    const val MAX_ATTEMPTS = 50

    val ALL = setOf(PENDING, IN_FLIGHT, FAILED, DEAD)

    /** Statuses reset by manual banner Retry (not healthy PENDING). */
    val STUCK_FOR_REQUEUE = setOf(DEAD, FAILED, IN_FLIGHT)

    fun isStuckForRequeue(status: String): Boolean = status in STUCK_FOR_REQUEUE
}

@Entity(tableName = "pending_samples")
data class PendingSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "tracking_id")
    val trackingId: String,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    val status: String = PendingSampleStatus.PENDING,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
