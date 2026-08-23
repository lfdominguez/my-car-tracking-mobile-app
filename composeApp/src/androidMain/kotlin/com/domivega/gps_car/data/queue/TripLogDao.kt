package com.domivega.gps_car.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TripLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TripLogEntity): Long

    @Query("SELECT * FROM trip_logs ORDER BY ended_at_ms DESC LIMIT :n")
    suspend fun getLast(n: Int): List<TripLogEntity>

    @Query(
        """
        DELETE FROM trip_logs
        WHERE id NOT IN (
            SELECT id FROM trip_logs ORDER BY ended_at_ms DESC LIMIT :keepCount
        )
        """
    )
    suspend fun deleteOldExcept(keepCount: Int)
}
