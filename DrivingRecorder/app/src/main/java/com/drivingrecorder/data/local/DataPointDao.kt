package com.drivingrecorder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.drivingrecorder.data.model.DataPointEntity

@Dao
interface DataPointDao {

    @Insert
    suspend fun insert(point: DataPointEntity): Long

    @Insert
    suspend fun insertAll(points: List<DataPointEntity>): List<Long>

    @Query("SELECT * FROM data_points WHERE trip_id = :tripId ORDER BY timestamp ASC")
    suspend fun getByTripId(tripId: Long): List<DataPointEntity>

    @Query("SELECT * FROM data_points WHERE trip_id = :tripId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getByTripIdPaged(tripId: Long, limit: Int, offset: Int): List<DataPointEntity>

    @Query("SELECT COUNT(*) FROM data_points WHERE trip_id = :tripId")
    suspend fun countByTripId(tripId: Long): Int

    @Query("DELETE FROM data_points WHERE trip_id = :tripId")
    suspend fun deleteByTripId(tripId: Long)
}
