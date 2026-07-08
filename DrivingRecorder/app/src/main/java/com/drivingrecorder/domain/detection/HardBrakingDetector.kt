package com.drivingrecorder.domain.detection

import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType

/**
 * 急刹车检测器
 *
 * 基于纵向加速度和速度下降率双重判断
 */
class HardBrakingDetector(
    private val config: DetectionConfig = DetectionConfig()
) {
    private var detecting = false
    private var detectStartTime = 0L
    private var detectStartSpeed = 0f
    private var peakDecel = 0f
    private var lastEventTime = 0L
    private var speedWindow = mutableListOf<Float>()  // 最近速度记录

    fun process(point: DataPoint): DrivingEvent? {
        val now = point.timestamp

        // 去抖动
        if (now - lastEventTime < config.hardBrakingDebounceMs) {
            return null
        }

        // 维护速度窗口
        speedWindow.add(point.speed)
        if (speedWindow.size > 10) speedWindow.removeFirst()

        val longitudinalAccel = point.longitudinalAccel

        if (!detecting) {
            // 检测急刹车开始：纵向减速度超过阈值
            if (longitudinalAccel <= config.hardBrakingDecelThreshold) {
                detecting = true
                detectStartTime = now
                detectStartSpeed = point.speed
                peakDecel = kotlin.math.abs(longitudinalAccel)
                speedWindow.clear()
                speedWindow.add(point.speed)
            }
            return null
        }

        // 正在检测中
        val elapsed = now - detectStartTime

        // 更新峰值减速度
        val absDecel = kotlin.math.abs(longitudinalAccel)
        if (absDecel > peakDecel) peakDecel = absDecel

        // 计算速度变化
        val speedDrop = detectStartSpeed - point.speed
        val speedDropRate = if (elapsed > 0) speedDrop / (elapsed / 1000f) else 0f

        // 减速度减弱或持续时间足够 → 判定结束
        val decelEnded = longitudinalAccel > -1.0f  // 减速度回到正常范围
        val minDurationMet = elapsed >= config.hardBrakingMinDurationMs

        if (decelEnded && minDurationMet) {
            detecting = false

            // 验证速度确实下降了
            val actualSpeedDrop = if (speedWindow.size >= 2) {
                speedWindow.first() - speedWindow.last()
            } else {
                speedDrop
            }

            // 确认是真正的急刹车（速度下降 > 5km/h）
            if (actualSpeedDrop >= 5f || speedDropRate >= kotlin.math.abs(config.hardBrakingSpeedDropRate)) {
                lastEventTime = now
                val severity = calculateSeverity(peakDecel, actualSpeedDrop, elapsed)

                return DrivingEvent(
                    tripId = point.tripId,
                    timestamp = point.timestamp,
                    eventType = EventType.HARD_BRAKING,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    speed = point.speed,
                    heading = point.heading,
                    severity = severity,
                    description = "急刹车: 减速度 %.1f m/s², 速度下降 %.1f km/h, 持续 %dms".format(
                        peakDecel, actualSpeedDrop, elapsed
                    )
                )
            }

            return null
        }

        // 超时（检测开始后2秒仍未结束）→ 放弃
        if (elapsed > 2000) {
            detecting = false
            return null
        }

        return null
    }

    private fun calculateSeverity(peakDecel: Float, speedDrop: Float, duration: Long): Float {
        val decelScore = (peakDecel / 8.0f).coerceIn(0f, 1f)   // 8 m/s² = 非常紧急
        val speedScore = (speedDrop / 30f).coerceIn(0f, 1f)     // 30km/h 下降
        val timeScore = (1f - duration / 2000f).coerceIn(0f, 1f) // 越短越紧急
        return (decelScore * 0.5f + speedScore * 0.3f + timeScore * 0.2f).coerceIn(0.1f, 1f)
    }

    fun reset() {
        detecting = false
        detectStartTime = 0
        detectStartSpeed = 0f
        peakDecel = 0f
        lastEventTime = 0L
        speedWindow.clear()
    }
}
