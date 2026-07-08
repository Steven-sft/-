package com.drivingrecorder.domain.model

/**
 * 驾驶行为事件
 */
data class DrivingEvent(
    val id: Long = 0,
    val tripId: Long = 0,
    val timestamp: Long,
    val eventType: EventType,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,               // km/h
    val heading: Float,             // degrees
    val severity: Float,            // 0.0 - 1.0 严重程度
    val description: String
)
