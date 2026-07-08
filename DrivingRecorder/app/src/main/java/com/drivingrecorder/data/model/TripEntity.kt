package com.drivingrecorder.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_time")
    val startTime: Long,            // epoch millis

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "max_speed")
    val maxSpeed: Float = 0f,       // km/h

    @ColumnInfo(name = "avg_speed")
    val avgSpeed: Float = 0f,       // km/h

    @ColumnInfo(name = "total_distance")
    val totalDistance: Float = 0f,  // meters

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "point_count")
    val pointCount: Int = 0,

    @ColumnInfo(name = "event_count")
    val eventCount: Int = 0
)
