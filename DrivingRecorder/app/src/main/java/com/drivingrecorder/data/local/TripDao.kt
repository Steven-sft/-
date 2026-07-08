package com.drivingrecorder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.drivingrecorder.data.model.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Query("SELECT * FROM trips ORDER BY start_time DESC")
    fun getAllFlow(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY start_time DESC")
    suspend fun getAll(): List<TripEntity>

    @Query("SELECT * FROM trips ORDER BY start_time DESC LIMIT 1")
    suspend fun getLatest(): TripEntity?

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE trips SET end_time = :endTime, max_speed = :maxSpeed, " +
           "avg_speed = :avgSpeed, total_distance = :totalDistance, " +
           "duration_ms = :durationMs, point_count = :pointCount, " +
           "event_count = :eventCount WHERE id = :id")
    suspend fun finalize(
        id: Long,
        endTime: Long,
        maxSpeed: Float,
        avgSpeed: Float,
        totalDistance: Float,
        durationMs: Long,
        pointCount: Int,
        eventCount: Int
    )
}
