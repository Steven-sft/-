package com.drivingrecorder.domain.detection

import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType

/**
 * 急加速检测器
 *
 * 基于纵向加速度（正值）和速度增加率判断
 */
class RapidAccelDetector(
    private val config: DetectionConfig = DetectionConfig()
) {
    private var detecting = false
    private var detectStartTime = 0L
    private var detectStartSpeed = 0f
    private var peakAccel = 0f
    private var lastEventTime = 0L
    private var speedWindow = mutableListOf<Float>()

    fun process(point: DataPoint): DrivingEvent? {
        val now = point.timestamp

        // 去抖动
        if (now - lastEventTime < config.rapidAccelDebounceMs) {
            return null
        }

        speedWindow.add(point.speed)
        if (speedWindow.size > 10) speedWindow.removeFirst()

        val longitudinalAccel = point.longitudinalAccel

        if (!detecting) {
            // 检测急加速开始
            if (point.speed > 5f &&  // 已有一定速度
                longitudinalAccel >= config.rapidAccelThreshold) {
                detecting = true
                detectStartTime = now
                detectStartSpeed = point.speed
                peakAccel = longitudinalAccel
                speedWindow.clear()
                speedWindow.add(point.speed)
            }
            return null
        }

        // 正在检测中
        val elapsed = now - detectStartTime

        if (longitudinalAccel > peakAccel) peakAccel = longitudinalAccel

        // 计算速度增加
        val speedIncrease = point.speed - detectStartSpeed
        val speedIncreaseRate = if (elapsed > 0) speedIncrease / (elapsed / 1000f) else 0f

        // 加速减弱或持续时间足够 → 判定结束
        val accelEnded = longitudinalAccel < 1.0f
        val minDurationMet = elapsed >= config.rapidAccelMinDurationMs

        if (accelEnded && minDurationMet) {
            detecting = false

            // 验证速度确实增加了（> 5km/h）
            if (speedIncrease >= 5f || speedIncreaseRate >= config.rapidAccelSpeedIncreaseRate) {
                lastEventTime = now
                val severity = calculateSeverity(peakAccel, speedIncrease, elapsed)

                return DrivingEvent(
                    tripId = point.tripId,
                    timestamp = point.timestamp,
                    eventType = EventType.RAPID_ACCELERATION,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    speed = point.speed,
                    heading = point.heading,
                    severity = severity,
                    description = "急加速: 加速度 %.1f m/s², 速度增加 %.1f km/h, 持续 %dms".format(
                        peakAccel, speedIncrease, elapsed
                    )
                )
            }
            return null
        }

        // 超时 → 放弃
        if (elapsed > 2000) {
            detecting = false
            return null
        }

        return null
    }

    private fun calculateSeverity(peakAccel: Float, speedIncrease: Float, duration: Long): Float {
        val accelScore = (peakAccel / 6f).coerceIn(0f, 1f)      // 6 m/s² 非常猛
        val speedScore = (speedIncrease / 25f).coerceIn(0f, 1f)  // 25km/h 增加
        val timeScore = (1f - duration / 2000f).coerceIn(0f, 1f)
        return (accelScore * 0.5f + speedScore * 0.3f + timeScore * 0.2f).coerceIn(0.1f, 1f)
    }

    fun reset() {
        detecting = false
        detectStartTime = 0L
        detectStartSpeed = 0f
        peakAccel = 0f
        lastEventTime = 0L
        speedWindow.clear()
    }
}
