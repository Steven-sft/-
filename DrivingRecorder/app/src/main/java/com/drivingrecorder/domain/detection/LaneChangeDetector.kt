package com.drivingrecorder.domain.detection

import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType
import com.drivingrecorder.util.SensorMathUtils

/**
 * 变道检测器
 *
 * 三信号融合算法：
 * 1. 横向加速度 — 检测横向移动
 * 2. 航向角变化 — 验证方向变化幅度
 * 3. 持续时间 — 区分变道和其他行为
 *
 * 状态机：
 *   IDLE → DETECTING (横向加速超过阈值) → CONFIRMED (航向变化确认) → IDLE
 *                  ↓ 超时/航向变化过大 → IDLE (判定为转弯)
 */
class LaneChangeDetector(
    private val config: DetectionConfig = DetectionConfig()
) {
    private var state = DetectionState.IDLE
    private var stateStartTime = 0L

    // 用于记录变道过程中的数据
    private var direction: EventType? = null
    private var startHeading = 0f
    private var peakLateralAccel = 0f
    private var latAccelHistory = mutableListOf<Float>()
    private var headingHistory = mutableListOf<Float>()

    data class DetectionResult(
        val state: DetectionState,
        val event: DrivingEvent?
    )

    enum class DetectionState {
        IDLE,           // 等待
        DETECTING,      // 检测到潜在变道，等待确认
        CONFIRMED       // 已确认变道（用于防重复触发）
    }

    fun process(point: DataPoint): DetectionResult {
        val now = point.timestamp

        latAccelHistory.add(point.lateralAccel)
        headingHistory.add(point.heading)
        // 保留最近 5 秒的数据
        val windowMs = 5000L
        latAccelHistory.removeAll { false } // 简单清理：保留所有，通过窗口判断
        if (latAccelHistory.size > 100) latAccelHistory = latAccelHistory.takeLast(50).toMutableList()
        if (headingHistory.size > 100) headingHistory = headingHistory.takeLast(50).toMutableList()

        when (state) {
            DetectionState.IDLE -> {
                return checkForLaneChangeStart(point, now)
            }

            DetectionState.DETECTING -> {
                return checkForLaneChangeConfirm(point, now)
            }

            DetectionState.CONFIRMED -> {
                // 等待横向加速度归零后再允许新的检测
                if (kotlin.math.abs(point.lateralAccel) < 0.3f ||
                    now - stateStartTime > 3000L) {
                    state = DetectionState.IDLE
                    latAccelHistory.clear()
                    headingHistory.clear()
                }
                return DetectionResult(state, null)
            }
        }
    }

    private fun checkForLaneChangeStart(
        point: DataPoint,
        now: Long
    ): DetectionResult {
        // 速度过低不检测
        if (point.speed < config.laneChangeMinSpeed) {
            return DetectionResult(DetectionState.IDLE, null)
        }

        val absLatAccel = kotlin.math.abs(point.lateralAccel)

        // 横向加速度超过阈值 → 进入检测状态
        if (absLatAccel >= config.laneChangeLateralAccelThreshold) {
            state = DetectionState.DETECTING
            stateStartTime = now
            direction = if (point.lateralAccel > 0) {
                EventType.LANE_CHANGE_RIGHT
            } else {
                EventType.LANE_CHANGE_LEFT
            }
            startHeading = point.heading
            peakLateralAccel = absLatAccel
            return DetectionResult(state, null)
        }

        return DetectionResult(DetectionState.IDLE, null)
    }

    private fun checkForLaneChangeConfirm(
        point: DataPoint,
        now: Long
    ): DetectionResult {
        val elapsed = now - stateStartTime

        // 更新峰值
        val absLatAccel = kotlin.math.abs(point.lateralAccel)
        if (absLatAccel > peakLateralAccel) {
            peakLateralAccel = absLatAccel
        }

        // 超时（超过最大变道时间）→ 可能是转弯，放弃
        if (elapsed > config.laneChangeMaxDurationMs) {
            state = DetectionState.CONFIRMED
            stateStartTime = now
            return DetectionResult(DetectionState.CONFIRMED, null)
        }

        // 计算累计航向变化
        val headingChange = SensorMathUtils.headingDelta(point.heading, startHeading)

        // 航向变化过大 → 可能是在转弯，放弃变道判定
        if (kotlin.math.abs(headingChange) > config.laneChangeHeadingMax) {
            state = DetectionState.CONFIRMED
            stateStartTime = now
            return DetectionResult(DetectionState.CONFIRMED, null)
        }

        // 横向加速度回落到接近零 AND 持续时间足够 → 变道完成
        val lateralAccelFading = absLatAccel < 0.5f
        val minDurationMet = elapsed >= config.laneChangeMinDurationMs
        val headingChangeOk = kotlin.math.abs(headingChange) >= config.laneChangeHeadingMin

        if (lateralAccelFading && minDurationMet && headingChangeOk) {
            state = DetectionState.CONFIRMED
            stateStartTime = now

            val severity = calculateLaneChangeSeverity(peakLateralAccel, elapsed)
            val desc = buildLaneChangeDescription(
                direction!!, peakLateralAccel, headingChange, elapsed
            )

            val event = DrivingEvent(
                tripId = point.tripId,
                timestamp = point.timestamp,
                eventType = direction!!,
                latitude = point.latitude,
                longitude = point.longitude,
                speed = point.speed,
                heading = point.heading,
                severity = severity,
                description = desc
            )

            return DetectionResult(DetectionState.CONFIRMED, event)
        }

        // 仍在检测中，等待更多数据
        return DetectionResult(DetectionState.DETECTING, null)
    }

    private fun calculateLaneChangeSeverity(
        peakLatAccel: Float,
        duration: Long
    ): Float {
        // 基于峰值横向加速度和持续时间计算严重程度
        val accelScore =
            (peakLatAccel / (config.laneChangeLateralAccelThreshold * 2)).coerceIn(0f, 1f)
        val durationScore =
            (duration.toFloat() / config.laneChangeMinDurationMs).coerceIn(0f, 1f)
        // 平滑的变道（长时间低加速度） vs 急促变道（短时间高加速度）
        val smoothness = (1f - kotlin.math.abs(accelScore - durationScore))
        return ((accelScore * 0.5f + durationScore * 0.2f + smoothness * 0.3f))
            .coerceIn(0.1f, 1f)
    }

    private fun buildLaneChangeDescription(
        direction: EventType,
        peakLatAccel: Float,
        headingChange: Float,
        duration: Long
    ): String {
        val dirText = if (direction == EventType.LANE_CHANGE_RIGHT) "右" else "左"
        val intensity = when {
            peakLatAccel > 3.0f -> "急促"
            peakLatAccel > 2.0f -> "正常"
            else -> "平缓"
        }
        return "${intensity}向${dirText}变道 (横向加速度: %.1f m/s², 航向变化: %.1f°, 持续: %dms)".format(
            peakLatAccel, headingChange, duration
        )
    }

    fun reset() {
        state = DetectionState.IDLE
        stateStartTime = 0
        direction = null
        startHeading = 0f
        peakLateralAccel = 0f
        latAccelHistory.clear()
        headingHistory.clear()
    }
}
