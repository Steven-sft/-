package com.drivingrecorder.domain.model

/**
 * 单次GPS+传感器数据采集点
 */
data class DataPoint(
    val id: Long = 0,
    val tripId: Long = 0,
    val timestamp: Long,            // epoch millis
    val latitude: Double,
    val longitude: Double,
    val speed: Float,               // km/h
    val heading: Float,             // degrees 0-360
    val accuracy: Float,            // meters
    val altitude: Double,           // meters
    val lateralAccel: Float,        // m/s² 横向加速度
    val longitudinalAccel: Float    // m/s² 纵向加速度
)
