package com.domivega.gps_car.data.queue

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingSampleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun pendingSampleDao(): PendingSampleDao

    companion object {
        private const val DB_NAME = "tracking.db"

        @Volatile
        private var instance: TrackingDatabase? = null

        fun getInstance(context: Context): TrackingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackingDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
        }
    }
}
