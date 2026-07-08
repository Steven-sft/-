package com.drivingrecorder.domain.detection

/**
 * 检测参数配置
 * 可通过设置界面调整灵敏度
 */
data class DetectionConfig(
    // 变道检测
    val laneChangeLateralAccelThreshold: Float = 1.5f,     // 横向加速度阈值 m/s² (~0.15g)
    val laneChangeMinDurationMs: Long = 800,                // 最小持续时间 ms
    val laneChangeMaxDurationMs: Long = 3500,               // 最大持续时间 ms
    val laneChangeHeadingMin: Float = 1.5f,                 // 最小航向变化（度）
    val laneChangeHeadingMax: Float = 8.0f,                 // 最大航向变化（度，排除转弯）
    val laneChangeMinSpeed: Float = 15f,                    // 最小速度 km/h

    // 急刹车检测
    val hardBrakingDecelThreshold: Float = -3.5f,           // 减速度阈值 m/s² (~ -0.35g)
    val hardBrakingSpeedDropRate: Float = -15f,             // 速度下降率 km/h/s
    val hardBrakingMinDurationMs: Long = 400,                // 最短持续时间 ms
    val hardBrakingDebounceMs: Long = 3000,                  // 去抖动间隔 ms

    // 急转弯检测
    val sharpTurnHeadingRate: Float = 25f,                   // 航向变化率 度/秒
    val sharpTurnLateralAccel: Float = 3.0f,                // 横向加速度阈值 m/s²
    val sharpTurnMinDurationMs: Long = 800,                  // 最短持续时间 ms

    // 急加速检测
    val rapidAccelThreshold: Float = 3.5f,                  // 加速度阈值 m/s² (~0.36g)
    val rapidAccelSpeedIncreaseRate: Float = 15f,            // 速度增加率 km/h/s
    val rapidAccelMinDurationMs: Long = 400,                 // 最短持续时间 ms
    val rapidAccelDebounceMs: Long = 3000                    // 去抖动间隔 ms
)
