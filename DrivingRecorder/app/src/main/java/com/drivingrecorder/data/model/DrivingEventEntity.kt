package com.drivingrecorder.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driving_events",
    indices = [Index(value = ["trip_id"])]
)
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "trip_id")
    val tripId: Long,

    val timestamp: Long,            // epoch millis

    @ColumnInfo(name = "event_type")
    val eventType: String,          // EventType.name

    val latitude: Double,

    val longitude: Double,

    val speed: Float,               // km/h

    val heading: Float,             // degrees

    val severity: Float,            // 0.0 to 1.0

    val description: String
)
