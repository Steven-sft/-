package com.drivingrecorder.util

import kotlin.math.*

/**
 * 传感器数学工具
 * 包含低通滤波、坐标变换、方向计算等
 */
object SensorMathUtils {

    /**
     * 一阶低通滤波器
     * @param current 当前原始值
     * @param previous 上一次滤波后的值
     * @param alpha 平滑系数 (0-1)，越接近1则响应越快
     */
    fun lowPassFilter(current: Float, previous: Float, alpha: Float = 0.8f): Float {
        return alpha * current + (1 - alpha) * previous
    }

    /**
     * 三轴低通滤波
     */
    fun lowPassFilter3(
        x: Float, y: Float, z: Float,
        prevX: Float, prevY: Float, prevZ: Float,
        alpha: Float = 0.8f
    ): Triple<Float, Float, Float> {
        return Triple(
            lowPassFilter(x, prevX, alpha),
            lowPassFilter(y, prevY, alpha),
            lowPassFilter(z, prevZ, alpha)
        )
    }

    /**
     * 计算航向角变化量（处理0/360度环绕）
     * @return 变化量（度），正值为顺时针
     */
    fun headingDelta(current: Float, previous: Float): Float {
        var delta = current - previous
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * 将手机坐标系加速度转换为车辆坐标系
     *
     * 假设手机固定安装：屏幕朝上，顶部朝车前方
     * 车辆坐标系（ISO 8855）:
     *   x: 前进方向
     *   y: 右侧
     *   z: 下方
     *
     * @param accelX 手机x轴加速度（右侧）
     * @param accelY 手机y轴加速度（前方）
     * @param headingRad GPS航向角（弧度）
     * @return Pair(lateralAccel, longitudinalAccel) 横向和纵向加速度
     */
    fun toVehicleCoordinates(
        accelX: Float,
        accelY: Float,
        headingRad: Double
    ): Pair<Float, Float> {
        // 手机y轴 → 车辆前进方向（纵向）
        // 手机x轴 → 车辆右侧（横向）
        val longitudinalAccel = accelY
        val lateralAccel = accelX
        return Pair(lateralAccel, longitudinalAccel)
    }

    /**
     * 计算移动窗口内的标准差
     */
    fun standardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance.toDouble()).toFloat()
    }

    /**
     * 检测过零点
     * 信号从正值变负值或反之
     */
    fun hasZeroCrossing(prev: Float, current: Float): Boolean {
        return (prev > 0 && current <= 0) || (prev < 0 && current >= 0)
    }

    /**
     * 计算平滑后的变化率
     * @param values 值序列（按时间排序）
     * @param dtSeconds 采样间隔（秒）
     * @return 变化率（单位/秒）
     */
    fun rateOfChange(values: List<Float>, dtSeconds: Float): Float {
        if (values.size < 2) return 0f
        return (values.last() - values.first()) / (values.size * dtSeconds)
    }
}
