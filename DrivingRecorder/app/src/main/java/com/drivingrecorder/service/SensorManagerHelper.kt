package com.drivingrecorder.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.drivingrecorder.util.SensorMathUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 传感器数据封装
 */
data class SensorData(
    val timestamp: Long,            // epoch millis
    val accelX: Float,              // 原始加速度 X (m/s²)
    val accelY: Float,              // 原始加速度 Y (m/s²)
    val accelZ: Float,              // 原始加速度 Z (m/s²)
    val filteredAccelX: Float,      // 滤波后加速度 X
    val filteredAccelY: Float,      // 滤波后加速度 Y
    val filteredAccelZ: Float,      // 滤波后加速度 Z
    val lateralAccel: Float,        // 横向加速度 (已转换到车辆坐标系)
    val longitudinalAccel: Float    // 纵向加速度 (已转换到车辆坐标系)
)

/**
 * 加速度传感器封装
 * 包含低通滤波和坐标转换
 */
class SensorManagerHelper(private val context: Context) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val linearAcceleration: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // 滤波状态
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f

    val hasAccelerometer: Boolean get() = accelerometer != null

    /**
     * 持续获取加速度数据的 Flow
     * 使用线性加速度传感器（已去除重力分量）优先
     */
    fun getSensorFlow(): Flow<SensorData> = callbackFlow {
        if (accelerometer == null) {
            // 无加速度传感器，发送零值
            trySend(createEmptySensorData())
            awaitClose {}
            return@callbackFlow
        }

        // 重置滤波状态
        filteredX = 0f
        filteredY = 0f
        filteredZ = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER ||
                    event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {

                    val rawX = event.values[0]
                    val rawY = event.values[1]
                    val rawZ = event.values[2]

                    // 一阶低通滤波（平滑噪声）
                    val (fx, fy, fz) = SensorMathUtils.lowPassFilter3(
                        rawX, rawY, rawZ,
                        filteredX, filteredY, filteredZ,
                        alpha = 0.85f
                    )
                    filteredX = fx
                    filteredY = fy
                    filteredZ = fz

                    // 转换到车辆坐标系
                    val (latAccel, lonAccel) = SensorMathUtils.toVehicleCoordinates(
                        fx, fy, 0.0 // heading 在外层由GPS提供
                    )

                    trySend(
                        SensorData(
                            timestamp = System.currentTimeMillis(),
                            accelX = rawX,
                            accelY = rawY,
                            accelZ = rawZ,
                            filteredAccelX = fx,
                            filteredAccelY = fy,
                            filteredAccelZ = fz,
                            lateralAccel = latAccel,
                            longitudinalAccel = lonAccel
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // 精度变化时不需要特殊处理
            }
        }

        // 优先使用线性加速度，否则使用普通加速度计
        val sensorToUse = linearAcceleration ?: accelerometer
        sensorManager.registerListener(
            listener,
            sensorToUse,
            SensorManager.SENSOR_DELAY_GAME  // 20ms 采样间隔（50Hz）
        )

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun createEmptySensorData() = SensorData(
        timestamp = System.currentTimeMillis(),
        accelX = 0f, accelY = 0f, accelZ = 0f,
        filteredAccelX = 0f, filteredAccelY = 0f, filteredAccelZ = 0f,
        lateralAccel = 0f, longitudinalAccel = 0f
    )
}
