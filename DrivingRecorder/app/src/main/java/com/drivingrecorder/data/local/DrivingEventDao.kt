package com.drivingrecorder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.drivingrecorder.data.model.DrivingEventEntity

@Dao
interface DrivingEventDao {

    @Insert
    suspend fun insert(event: DrivingEventEntity): Long

    @Query("SELECT * FROM driving_events WHERE trip_id = :tripId ORDER BY timestamp ASC")
    suspend fun getByTripId(tripId: Long): List<DrivingEventEntity>

    @Query("SELECT COUNT(*) FROM driving_events WHERE trip_id = :tripId")
    suspend fun countByTripId(tripId: Long): Int

    @Query("DELETE FROM driving_events WHERE trip_id = :tripId")
    suspend fun deleteByTripId(tripId: Long)
}
