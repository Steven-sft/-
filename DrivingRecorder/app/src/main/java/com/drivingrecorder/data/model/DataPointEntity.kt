package com.drivingrecorder.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "data_points",
    indices = [Index(value = ["trip_id"])]
)
data class DataPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "trip_id")
    val tripId: Long,

    val timestamp: Long,            // epoch millis

    val latitude: Double,

    val longitude: Double,

    val speed: Float,               // km/h

    val heading: Float,             // degrees 0-360

    val accuracy: Float,            // meters

    val altitude: Double,           // meters

    @ColumnInfo(name = "lat_accel")
    val lateralAccel: Float,        // m/s²

    @ColumnInfo(name = "lon_accel")
    val longitudinalAccel: Float    // m/s²
)
