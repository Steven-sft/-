package com.drivingrecorder.util

import kotlin.math.*

/**
 * 地理位置计算工具
 */
object LocationUtils {

    private const val EARTH_RADIUS_M = 6371000.0

    /**
     * Haversine 公式计算两点间距离（米）
     */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (EARTH_RADIUS_M * c).toFloat()
    }

    /**
     * 计算从点1到点2的方位角（度，0-360）
     */
    fun bearingDegrees(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) -
                sin(rLat1) * cos(rLat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }

    /**
     * 米/秒 转 公里/小时
     */
    fun msToKmh(ms: Float): Float = ms * 3.6f

    /**
     * 公里/小时 转 米/秒
     */
    fun kmhToMs(kmh: Float): Float = kmh / 3.6f
}
