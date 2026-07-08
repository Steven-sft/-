package com.drivingrecorder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.drivingrecorder.data.model.DataPointEntity
import com.drivingrecorder.data.model.DrivingEventEntity
import com.drivingrecorder.data.model.TripEntity

@Database(
    entities = [
        TripEntity::class,
        DataPointEntity::class,
        DrivingEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun dataPointDao(): DataPointDao
    abstract fun drivingEventDao(): DrivingEventDao

    companion object {
        private const val DATABASE_NAME = "driving_recorder.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
