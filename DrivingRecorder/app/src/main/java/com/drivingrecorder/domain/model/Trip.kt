package com.drivingrecorder.domain.model

/**
 * 行程汇总信息
 */
data class Trip(
    val id: Long = 0,
    val startTime: Long,            // epoch millis
    val endTime: Long? = null,
    val maxSpeed: Float = 0f,       // km/h
    val avgSpeed: Float = 0f,       // km/h
    val totalDistance: Float = 0f,  // meters
    val durationMs: Long = 0,
    val pointCount: Int = 0,
    val eventCount: Int = 0
)
