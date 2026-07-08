package com.drivingrecorder.domain.detection

import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent

/**
 * 驾驶行为分析编排器
 *
 * 协调所有检测器，维护滑动窗口，去重事件
 */
class DrivingBehaviorAnalyzer(
    private val config: DetectionConfig = DetectionConfig()
) {
    private val laneChangeDetector = LaneChangeDetector(config)
    private val hardBrakingDetector = HardBrakingDetector(config)
    private val sharpTurnDetector = SharpTurnDetector(config)
    private val rapidAccelDetector = RapidAccelDetector(config)

    // 滑动窗口：保留最近10秒的数据点用于批量分析
    private val slidingWindow = ArrayDeque<DataPoint>(MAX_WINDOW_SIZE)
    private var lastEventTime = 0L

    /**
     * 处理新的数据点
     * @return 检测到的驾驶事件列表（可能为空）
     */
    fun onNewDataPoint(point: DataPoint): List<DrivingEvent> {
        slidingWindow.addLast(point)
        if (slidingWindow.size > MAX_WINDOW_SIZE) {
            slidingWindow.removeFirst()
        }

        val events = mutableListOf<DrivingEvent>()

        // 依次调用各检测器
        val laneResult = laneChangeDetector.process(point)
        laneResult.event?.let { event ->
            if (isNewEvent(event)) {
                events.add(event)
            }
        }

        hardBrakingDetector.process(point)?.let { event ->
            if (isNewEvent(event)) {
                events.add(event)
            }
        }

        sharpTurnDetector.process(point)?.let { event ->
            if (isNewEvent(event)) {
                events.add(event)
            }
        }

        rapidAccelDetector.process(point)?.let { event ->
            if (isNewEvent(event)) {
                events.add(event)
            }
        }

        return events
    }

    /**
     * 事件去重：同一秒内不重复触发同类型事件
     */
    private fun isNewEvent(event: DrivingEvent): Boolean {
        if (event.timestamp - lastEventTime < 500) {
            return false
        }
        lastEventTime = event.timestamp
        return true
    }

    /**
     * 获取滑动窗口中的数据点（供外部分析使用）
     */
    fun getRecentPoints(): List<DataPoint> = slidingWindow.toList()

    /**
     * 重置所有检测器状态（新行程开始前调用）
     */
    fun reset() {
        laneChangeDetector.reset()
        hardBrakingDetector.reset()
        sharpTurnDetector.reset()
        rapidAccelDetector.reset()
        slidingWindow.clear()
        lastEventTime = 0L
    }

    companion object {
        private const val MAX_WINDOW_SIZE = 200  // 1秒1个点 ≈ 200秒窗口
    }
}
