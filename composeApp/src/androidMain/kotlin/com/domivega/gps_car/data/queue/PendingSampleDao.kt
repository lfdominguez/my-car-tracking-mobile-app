package com.domivega.gps_car.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSampleEntity): Long

    /**
     * Returns up to [limit] rows that are ready to flush (PENDING or FAILED),
     * oldest first. Excludes IN_FLIGHT, DEAD, and unbound local session ids.
     */
    @Query(
        """
        SELECT * FROM pending_samples
        WHERE status IN ('PENDING', 'FAILED')
          AND tracking_id NOT LIKE 'local:%'
        ORDER BY recorded_at ASC
        LIMIT :limit
        """
    )
    suspend fun getBatch(limit: Int): List<PendingSampleEntity>

    @Query(
        """
        UPDATE pending_samples
        SET status = 'IN_FLIGHT'
        WHERE id IN (:ids)
        """
    )
    suspend fun markInFlight(ids: List<Long>)

    @Query(
        """
        DELETE FROM pending_samples
        WHERE id IN (:ids)
        """
    )
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Increments attempts and stores [error].
     * Rows reaching [PendingSampleStatus.MAX_ATTEMPTS] become DEAD; others FAILED.
     */
    @Query(
        """
        UPDATE pending_samples
        SET
            attempts = attempts + 1,
            last_error = :error,
            status = CASE
                WHEN attempts + 1 >= ${PendingSampleStatus.MAX_ATTEMPTS} THEN 'DEAD'
                ELSE 'FAILED'
            END
        WHERE id IN (:ids)
        """
    )
    suspend fun markFailed(ids: List<Long>, error: String)

    @Query(
        """
        SELECT COUNT(*) FROM pending_samples
        WHERE status IN ('PENDING', 'FAILED')
        """
    )
    suspend fun countPending(): Int

    @Query(
        """
        SELECT COUNT(*) FROM pending_samples
        WHERE status = :status
        """
    )
    suspend fun countByStatus(status: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM pending_samples
        WHERE status IN ('PENDING', 'FAILED')
          AND tracking_id NOT LIKE 'local:%'
        """
    )
    suspend fun countUploadable(): Int

    @Query(
        """
        SELECT * FROM pending_samples
        WHERE tracking_id = :trackingId
        """
    )
    suspend fun getByTrackingId(trackingId: String): List<PendingSampleEntity>

    @Query(
        """
        UPDATE pending_samples
        SET tracking_id = :newTrackingId,
            payload_json = :payloadJson
        WHERE id = :id
        """
    )
    suspend fun updatePayloadAndTrackingId(id: Long, newTrackingId: String, payloadJson: String)

    /** Transient network failure: return to PENDING without burning attempts. */
    @Query(
        """
        UPDATE pending_samples
        SET status = 'PENDING'
        WHERE id IN (:ids)
        """
    )
    suspend fun restoreToPending(ids: List<Long>)

    /** Crash recovery: any leftover IN_FLIGHT rows become PENDING again. */
    @Query(
        """
        UPDATE pending_samples
        SET status = 'PENDING'
        WHERE status = 'IN_FLIGHT'
        """
    )
    suspend fun resetInFlightToPending()
}
