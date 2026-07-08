package com.drivingrecorder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.drivingrecorder.MainActivity
import com.drivingrecorder.R
import com.drivingrecorder.data.local.AppDatabase
import com.drivingrecorder.data.model.DataPointEntity
import com.drivingrecorder.data.model.DrivingEventEntity
import com.drivingrecorder.data.repository.RecordingRepository
import com.drivingrecorder.data.repository.TripRepository
import com.drivingrecorder.domain.detection.DrivingBehaviorAnalyzer
import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.util.LocationUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 前台录制服务
 * 在后台持续采集GPS+传感器数据，检测驾驶行为，写入数据库
 */
class RecordingForegroundService : LifecycleService() {

    private lateinit var locationHelper: LocationHelper
    private lateinit var sensorHelper: SensorManagerHelper
    private lateinit var repository: TripRepository
    private lateinit var behaviorAnalyzer: DrivingBehaviorAnalyzer

    private var tripId: Long = -1
    private var startTime: Long = 0
    private var lastSensorData: SensorData? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val recordingRepo = RecordingRepository()

    override fun onCreate() {
        super.onCreate()
        locationHelper = LocationHelper(this)
        sensorHelper = SensorManagerHelper(this)
        val db = AppDatabase.getInstance(this)
        repository = TripRepository(db)
        behaviorAnalyzer = DrivingBehaviorAnalyzer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1)
                if (tripId > 0) {
                    startRecording()
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun startRecording() {
        startForeground()
        acquireWakeLock()
        recordingRepo.startRecording(tripId)
        startTime = System.currentTimeMillis()

        lifecycleScope.launch {
            // 启动时长计时器
            launch { elapsedTimer() }

            // 启动传感器采集
            launch { collectSensors() }

            // 启动GPS采集（GPS回调中整合所有数据）
            launch { collectLocation() }
        }
    }

    private suspend fun collectLocation() {
        locationHelper.getLocationFlow().collect { location ->
            if (!recordingRepo.isRecording.value) return@collect

            val sensorData = lastSensorData ?: SensorData(
                System.currentTimeMillis(),
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
            )

            val heading = if (location.hasBearing()) location.bearing else 0f
            val speedKmh = if (location.hasSpeed()) {
                LocationUtils.msToKmh(location.speed)
            } else 0f

            val dataPoint = DataPoint(
                tripId = tripId,
                timestamp = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                speed = speedKmh,
                heading = heading,
                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                lateralAccel = sensorData.lateralAccel,
                longitudinalAccel = sensorData.longitudinalAccel
            )

            // 更新实时数据
            recordingRepo.onNewDataPoint(dataPoint)

            // 写入缓冲区并检查是否需要批量写入
            val batch = recordingRepo.onNewDataPoint(dataPoint)
            if (batch != null && batch.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.insertDataPoints(batch)
                }

                // 运行行为检测
                val domainPoints = batch.map { entity ->
                    DataPoint(
                        id = entity.id,
                        tripId = entity.tripId,
                        timestamp = entity.timestamp,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        speed = entity.speed,
                        heading = entity.heading,
                        accuracy = entity.accuracy,
                        altitude = entity.altitude,
                        lateralAccel = entity.lateralAccel,
                        longitudinalAccel = entity.longitudinalAccel
                    )
                }

                for (point in domainPoints) {
                    val events = behaviorAnalyzer.onNewDataPoint(point)
                    for (event in events) {
                        recordingRepo.addEvent(event)
                        withContext(Dispatchers.IO) {
                            repository.insertEvent(
                                DrivingEventEntity(
                                    tripId = event.tripId,
                                    timestamp = event.timestamp,
                                    eventType = event.eventType.name,
                                    latitude = event.latitude,
                                    longitude = event.longitude,
                                    speed = event.speed,
                                    heading = event.heading,
                                    severity = event.severity,
                                    description = event.description
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun collectSensors() {
        sensorHelper.getSensorFlow().collect { sensorData ->
            lastSensorData = sensorData
        }
    }

    private suspend fun elapsedTimer() {
        while (recordingRepo.isRecording.value) {
            delay(1000)
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            recordingRepo.updateElapsed(elapsed)
            updateNotification(elapsed)
        }
    }

    private fun stopRecording() {
        lifecycleScope.launch {
            recordingRepo.stopRecording()
            locationHelper.stopLocationUpdates()

            // 写入剩余缓冲区数据
            val remaining = recordingRepo.drainBuffer()
            if (remaining.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.insertDataPoints(remaining)
                }
            }

            // 汇总行程
            withContext(Dispatchers.IO) {
                repository.finalizeTrip(tripId)
            }

            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForeground() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(elapsed: Long) {
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        val timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        val distance = "%.1f".format(recordingRepo.totalDistance.value / 1000f)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("录制中 $timeStr")
            .setContentText("距离: ${distance}km | 速度: ${"%.0f".format(recordingRepo.currentSpeed.value)}km/h")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "驾驶记录后台服务通知"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DrivingRecorder:RecordingWakeLock"
        )
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 最大24小时
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        locationHelper.stopLocationUpdates()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "driving_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.drivingrecorder.START_RECORDING"
        const val ACTION_STOP = "com.drivingrecorder.STOP_RECORDING"
        const val EXTRA_TRIP_ID = "trip_id"

        fun startService(context: Context, tripId: Long) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
