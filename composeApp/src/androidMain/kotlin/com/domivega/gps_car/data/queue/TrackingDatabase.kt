package com.domivega.gps_car.data.queue

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PendingSampleEntity::class, TripLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun pendingSampleDao(): PendingSampleDao
    abstract fun tripLogDao(): TripLogDao

    companion object {
        private const val DB_NAME = "tracking.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trip_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tracking_id TEXT NOT NULL,
                        started_at_ms INTEGER NOT NULL,
                        ended_at_ms INTEGER NOT NULL,
                        log_text TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: TrackingDatabase? = null

        fun getInstance(context: Context): TrackingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackingDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
        }
    }
}
