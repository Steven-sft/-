package com.drivingrecorder.data.repository

import com.drivingrecorder.data.model.DataPointEntity
import com.drivingrecorder.data.model.DrivingEventEntity
import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 录制过程中的实时数据仓库
 * 维护内存中的最近数据点窗口和事件列表
 */
class RecordingRepository {

    // 当前录制状态
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // 当前行程 ID
    private val _currentTripId = MutableStateFlow<Long?>(null)
    val currentTripId: StateFlow<Long?> = _currentTripId.asStateFlow()

    // 最近的数据点（用于UI实时显示）
    private val _latestDataPoint = MutableStateFlow<DataPoint?>(null)
    val latestDataPoint: StateFlow<DataPoint?> = _latestDataPoint.asStateFlow()

    // 最近的速度（km/h）
    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    // 最近的航向角
    private val _currentHeading = MutableStateFlow(0f)
    val currentHeading: StateFlow<Float> = _currentHeading.asStateFlow()

    // 累计距离（米）
    private val _totalDistance = MutableStateFlow(0f)
    val totalDistance: StateFlow<Float> = _totalDistance.asStateFlow()

    // 已经过的秒数
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    // 最近检测到的事件
    private val _recentEvents = MutableStateFlow<List<DrivingEvent>>(emptyList())
    val recentEvents: StateFlow<List<DrivingEvent>> = _recentEvents.asStateFlow()

    // 批量插入缓冲区
    private val dataPointBuffer = mutableListOf<DataPointEntity>()
    private var lastInsertTime = 0L
    private var previousPoint: DataPoint? = null

    fun startRecording(tripId: Long) {
        _isRecording.value = true
        _currentTripId.value = tripId
        _latestDataPoint.value = null
        _currentSpeed.value = 0f
        _currentHeading.value = 0f
        _totalDistance.value = 0f
        _elapsedSeconds.value = 0L
        _recentEvents.value = emptyList()
        dataPointBuffer.clear()
        lastInsertTime = System.currentTimeMillis()
        previousPoint = null
    }

    fun stopRecording() {
        _isRecording.value = false
        _currentTripId.value = null
    }

    /**
     * 添加新的数据点，返回是否需要批量写入
     */
    fun onNewDataPoint(point: DataPoint): List<DataPointEntity>? {
        _latestDataPoint.value = point
        _currentSpeed.value = point.speed
        _currentHeading.value = point.heading

        // 更新累计距离
        previousPoint?.let { prev ->
            val dist = TripRepository.haversineDistance(
                prev.latitude, prev.longitude,
                point.latitude, point.longitude
            )
            _totalDistance.value = _totalDistance.value + dist
        }
        previousPoint = point

        val entity = point.toEntity()
        dataPointBuffer.add(entity)

        // 每0.5秒或累积20个点后批量写入（高频采集模式）
        val now = System.currentTimeMillis()
        return if (dataPointBuffer.size >= 20 || now - lastInsertTime >= 500) {
            val batch = dataPointBuffer.toList()
            dataPointBuffer.clear()
            lastInsertTime = now
            batch
        } else {
            null
        }
    }

    fun drainBuffer(): List<DataPointEntity> {
        val batch = dataPointBuffer.toList()
        dataPointBuffer.clear()
        return batch
    }

    fun addEvent(event: DrivingEvent) {
        val current = _recentEvents.value.toMutableList()
        current.add(0, event)
        // 保留最近50个事件
        if (current.size > 50) current.removeAt(current.size - 1)
        _recentEvents.value = current
    }

    fun updateElapsed(seconds: Long) {
        _elapsedSeconds.value = seconds
    }

    private fun DataPoint.toEntity() = DataPointEntity(
        tripId = tripId,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        speed = speed,
        heading = heading,
        accuracy = accuracy,
        altitude = altitude,
        lateralAccel = lateralAccel,
        longitudinalAccel = longitudinalAccel
    )
}
