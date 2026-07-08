package com.drivingrecorder.domain.detection

import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType
import com.drivingrecorder.util.SensorMathUtils

/**
 * 急转弯检测器
 *
 * 基于航向角变化率和横向加速度判断
 */
class SharpTurnDetector(
    private val config: DetectionConfig = DetectionConfig()
) {
    private var detecting = false
    private var detectStartTime = 0L
    private var previousHeading = -1f
    private var previousTime = 0L
    private var cumulativeHeadingChange = 0f
    private var peakHeadingRate = 0f
    private var peakLateralAccel = 0f
    private var lastEventTime = 0L

    fun process(point: DataPoint): DrivingEvent? {
        val now = point.timestamp

        // 初始化
        if (previousHeading < 0f) {
            previousHeading = point.heading
            previousTime = now
            return null
        }

        // 计算航向变化率
        val dt = (now - previousTime) / 1000f
        if (dt <= 0f) return null

        val headingDelta = SensorMathUtils.headingDelta(point.heading, previousHeading)
        val headingRate = kotlin.math.abs(headingDelta) / dt

        previousHeading = point.heading
        previousTime = now

        val absLatAccel = kotlin.math.abs(point.lateralAccel)

        if (!detecting) {
            // 检测急转弯开始
            val sharpTurnDetected =
                headingRate >= config.sharpTurnHeadingRate &&
                        absLatAccel >= config.sharpTurnLateralAccel

            if (sharpTurnDetected) {
                detecting = true
                detectStartTime = now
                cumulativeHeadingChange = 0f
                peakHeadingRate = headingRate
                peakLateralAccel = absLatAccel
            }
            return null
        }

        // 正在检测中
        val elapsed = now - detectStartTime
        cumulativeHeadingChange += headingDelta

        if (headingRate > peakHeadingRate) peakHeadingRate = headingRate
        if (absLatAccel > peakLateralAccel) peakLateralAccel = absLatAccel

        // 转向结束：航向变化率降低
        val turnEnded = headingRate < config.sharpTurnHeadingRate * 0.4f
        val minDurationMet = elapsed >= config.sharpTurnMinDurationMs

        if (turnEnded && minDurationMet) {
            detecting = false

            // 确认是否是真的急转弯（累计航向变化 > 15度）
            val absHeadingChange = kotlin.math.abs(cumulativeHeadingChange)
            if (absHeadingChange >= 15f) {
                // 去抖动
                if (now - lastEventTime < 2000) return null
                lastEventTime = now

                val direction = if (cumulativeHeadingChange > 0) {
                    EventType.SHARP_TURN_RIGHT
                } else {
                    EventType.SHARP_TURN_LEFT
                }

                val severity = calculateSeverity(
                    peakHeadingRate, peakLateralAccel, absHeadingChange
                )

                return DrivingEvent(
                    tripId = point.tripId,
                    timestamp = point.timestamp,
                    eventType = direction,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    speed = point.speed,
                    heading = point.heading,
                    severity = severity,
                    description = "急转弯: 航向变化率 %.1f°/s, 累计转角 %.1f°, 横向加速度 %.1f m/s²".format(
                        peakHeadingRate, absHeadingChange, peakLateralAccel
                    )
                )
            }
            return null
        }

        // 超时 → 放弃
        if (elapsed > 5000) {
            detecting = false
            return null
        }

        return null
    }

    private fun calculateSeverity(
        headingRate: Float,
        lateralAccel: Float,
        totalAngle: Float
    ): Float {
        val rateScore = (headingRate / 50f).coerceIn(0f, 1f)     // 50°/s 非常急
        val accelScore = (lateralAccel / 5f).coerceIn(0f, 1f)    // 5 m/s² ≈ 0.5g
        val angleScore = (totalAngle / 90f).coerceIn(0f, 1f)     // 90° 大转弯
        return (rateScore * 0.4f + accelScore * 0.3f + angleScore * 0.3f)
            .coerceIn(0.1f, 1f)
    }

    fun reset() {
        detecting = false
        detectStartTime = 0L
        previousHeading = -1f
        previousTime = 0L
        cumulativeHeadingChange = 0f
        peakHeadingRate = 0f
        peakLateralAccel = 0f
        lastEventTime = 0L
    }
}
