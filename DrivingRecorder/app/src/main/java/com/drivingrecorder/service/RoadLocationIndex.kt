package com.drivingrecorder.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*

/**
 * 道路中心线里程数据点
 */
data class RoadPoint(
    val latitude: Double,
    val longitude: Double,
    val location: String,    // 桩号 e.g. "AK0+0", "K1100+000"
    val locationNum: Double, // 里程数值(米)
    val ramp: String,        // 匝道/线路 e.g. "A", ""
    val laneNum: Int,        // 车道号
    val direction: Int       // 方向
)

/**
 * 空间索引 + 里程查询引擎
 *
 * 使用网格哈希实现快速最近邻查找:
 * - 网格精度 0.002° ≈ 220m
 * - 查询当前格 + 8 邻格中的最近点
 * - 小文件全量加载，大文件流式解析
 */
class RoadLocationIndex(private val context: Context) {
    private val allPoints = ArrayList<RoadPoint>(600000)
    private val grid = HashMap<Long, ArrayList<Int>>()  // gridKey → indices
    private var loaded = false
    private val gridSize = 0.002  // 度

    companion object {
        private const val TAG = "RoadLocationIndex"
    }

    /** 异步初始化索引 */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        try {
            // 1. 加载小文件（全量加载，内存占用小）
            loadSmallFile("3-XG-ABCDK_locations_supplement.json")
            // 2. 流式加载大文件（50MB，逐条解析避免OOM）
            loadLargeFile("lane_location_centerline_0607.json")
            loaded = true
            Log.i(TAG, "Road index ready: ${allPoints.size} points, ${grid.size} grid cells")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load road data", e)
        }
    }

    private fun loadSmallFile(filename: String) {
        val json = context.assets.open(filename).bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val point = RoadPoint(
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                location = obj.getString("location"),
                locationNum = obj.getDouble("locationNum") * 1000, // km → m
                ramp = obj.optString("ramp", ""),
                laneNum = obj.optInt("laneNum", 1),
                direction = obj.optInt("direction", 1)
            )
            addPoint(point)
        }
    }

    private fun loadLargeFile(filename: String) {
        val reader = BufferedReader(InputStreamReader(context.assets.open(filename)), 8192 * 8)
        reader.use { br ->
            // 跳过前导 {"RECORDS":[
            br.readLine(); br.readLine()
            var line: String?
            var count = 0
            while (br.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed == "]" || trimmed == "}") continue
                // 移除行尾逗号
                val clean = trimmed.removeSuffix(",")
                try {
                    val obj = JSONObject(clean)
                    val point = RoadPoint(
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        location = obj.getString("location"),
                        locationNum = obj.getDouble("location_num"),
                        ramp = "",
                        laneNum = obj.optString("lane_num", "1").toIntOrNull() ?: 1,
                        direction = obj.optString("direction", "1").toIntOrNull() ?: 1
                    )
                    addPoint(point)
                    count++
                    if (count % 100000 == 0) Log.d(TAG, "Loaded $count points...")
                } catch (_: Exception) { /* skip malformed lines */ }
            }
            Log.i(TAG, "Large file loaded: $count points")
        }
    }

    private fun addPoint(point: RoadPoint) {
        val idx = allPoints.size
        allPoints.add(point)
        val key = gridKey(point.latitude, point.longitude)
        grid.getOrPut(key) { ArrayList(16) }.add(idx)
    }

    private fun gridKey(lat: Double, lng: Double): Long {
        val x = (lng / gridSize).toLong()
        val y = (lat / gridSize).toLong()
        return (x shl 32) or (y and 0xFFFFFFFF)
    }

    /**
     * 查询指定 GPS 位置最近的道路中心线点
     * @return RoadPoint? 最近的点，包含桩号和车道信息
     */
    fun queryNearest(lat: Double, lng: Double): RoadPoint? {
        if (!loaded || allPoints.isEmpty()) return null

        val key = gridKey(lat, lng)
        var bestPoint: RoadPoint? = null
        var bestDist = Double.MAX_VALUE

        // 搜索当前格 + 8 邻格
        for (dx in -1L..1L) {
            for (dy in -1L..1L) {
                val searchKey = key + (dx shl 32) + dy
                val indices = grid[searchKey] ?: continue
                for (idx in indices) {
                    val p = allPoints[idx]
                    val dist = haversineApprox(lat, lng, p.latitude, p.longitude)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestPoint = p
                    }
                }
            }
        }
        return bestPoint
    }

    /** 快速近似距离（平面坐标，适用于小范围） */
    private fun haversineApprox(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = (lat2 - lat1) * 111320.0
        val dLng = (lng2 - lng1) * 111320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
        return dLat * dLat + dLng * dLng  // 平方距离，无需开方
    }

    val isLoaded: Boolean get() = loaded
    val pointCount: Int get() = allPoints.size
}
