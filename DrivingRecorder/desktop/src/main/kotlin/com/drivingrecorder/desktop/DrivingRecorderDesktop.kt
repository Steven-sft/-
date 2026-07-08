package com.drivingrecorder.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.*
import kotlin.random.Random as KRandom
import kotlinx.serialization.json.*

// ==================== Device Markers ====================

data class DeviceMarker(
    val lat: Double, val lng: Double,
    val label: String,        // 设备编号/名称
    val type: String,         // "雷视" or "卡口"
    val info: String          // 详细信息
)

fun loadLeishiDevices(roadIndex: DesktopRoadIndex): List<DeviceMarker> {
    val text = object{}.javaClass.classLoader.getResourceAsStream("3-雷视设备安装情况.json")?.bufferedReader()?.use { it.readText() } ?: return emptyList()
    val arr = Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonArray
    val markers = mutableListOf<DeviceMarker>()
    val seen = mutableSetOf<String>()

    for (el in arr) {
        val obj = el.jsonObject
        val station = obj["最终位置"]?.jsonPrimitive?.content ?: continue
        if (station.isBlank() || station == "") continue
        val devType = obj["图纸设备类型"]?.jsonPrimitive?.content ?: ""
        val devName = obj["设备名称"]?.jsonPrimitive?.content ?: ""
        val toll = obj["归属收费站"]?.jsonPrimitive?.content ?: ""
        val dir = obj["站点位置（北京向左幅，港澳向右幅）"]?.jsonPrimitive?.content ?: ""

        // Parse桩号 (e.g. "K1030+800" → 1030800 meters)
        val stationMatch = Regex("""[Kk]?(\d+)\+(\d+)""").find(station) ?: continue
        val km = stationMatch.groupValues[1].toDoubleOrNull() ?: continue
        val m = stationMatch.groupValues[2].toDoubleOrNull() ?: continue
        val locationNum = km * 1000 + m

        // Look up coordinates from road index
        val nearest = roadIndex.queryNearestByStation(locationNum)
        // 只保留桩号差距在500m以内的匹配（避免跨路段错误匹配）
        if (nearest != null && kotlin.math.abs(nearest.locationNum - locationNum) < 500) {
            val key = "${devType}_${station}"
            if (seen.add(key)) {
                markers.add(DeviceMarker(
                    lat = nearest.lat, lng = nearest.lng,
                    label = "$devType($devName)",
                    type = "雷视",
                    info = "桩号:$station 收费站:$toll 方向:$dir"
                ))
            }
        }
    }
    return markers
}

fun loadCheckpointDevices(): List<DeviceMarker> {
    val text = object{}.javaClass.classLoader.getResourceAsStream("试验段卡口IP映射.json")?.bufferedReader()?.use { it.readText() } ?: return emptyList()
    val arr = Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonArray
    val markers = mutableListOf<DeviceMarker>()
    val seen = mutableSetOf<String>()

    for (el in arr) {
        val obj = el.jsonObject
        val id = obj["卡口编号"]?.jsonPrimitive?.content ?: continue
        val lnglat = obj["lnglat"]?.jsonArray ?: continue
        if (lnglat.size < 2) continue
        val lng = lnglat[0].jsonPrimitive.double
        val lat = lnglat[1].jsonPrimitive.double
        val station = obj["里程桩号"]?.jsonPrimitive?.content ?: ""
        val dir = obj["方向"]?.jsonPrimitive?.content ?: ""
        val ip = obj["ip"]?.jsonPrimitive?.content ?: ""

        if (seen.add(id)) {
            markers.add(DeviceMarker(
                lat = lat, lng = lng,
                label = id,
                type = "卡口",
                info = "桩号:$station 方向:$dir IP:$ip"
            ))
        }
    }
    return markers
}

// ==================== Road Index (Desktop) ====================

class DesktopRoadIndex {
    data class RoadPt(val lat: Double, val lng: Double, val location: String, val ramp: String, val locationNum: Double)

    private val points = mutableListOf<RoadPt>()
    private val grid = HashMap<Long, MutableList<Int>>()
    private val gridSize = 0.002
    var loaded = false; private set

    fun loadFromResource(path: String) {
        val text = object{}.javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() } ?: return
        val arr = Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonArray
        for (el in arr) {
            val obj = el.jsonObject
            val pt = RoadPt(
                lat = obj["latitude"]!!.jsonPrimitive.double,
                lng = obj["longitude"]!!.jsonPrimitive.double,
                location = obj["location"]!!.jsonPrimitive.content,
                ramp = obj["ramp"]?.jsonPrimitive?.content ?: "",
                locationNum = obj["locationNum"]!!.jsonPrimitive.double * 1000.0
            )
            val idx = points.size; points.add(pt)
            val key = ((pt.lng / gridSize).toLong() shl 32) or ((pt.lat / gridSize).toLong() and 0xFFFFFFFF)
            grid.getOrPut(key) { mutableListOf() }.add(idx)
        }
        loaded = true
    }

    /** 通过桩号里程数值查询最近点 */
    fun queryNearestByStation(stationMeters: Double): RoadPt? {
        if (!loaded || points.isEmpty()) return null
        return points.minByOrNull { abs(it.locationNum - stationMeters) }
    }

    fun queryNearest(lat: Double, lng: Double): RoadPt? {
        if (!loaded || points.isEmpty()) return null
        val key = ((lng / gridSize).toLong() shl 32) or ((lat / gridSize).toLong() and 0xFFFFFFFF)
        var best: RoadPt? = null; var bestD = Double.MAX_VALUE
        for (dx in -1L..1L) for (dy in -1L..1L) {
            val indices = grid[key + (dx shl 32) + dy] ?: continue
            for (i in indices) {
                val p = points[i]
                val dLat = (p.lat - lat) * 111320.0; val dLng = (p.lng - lng) * 111320.0 * cos(Math.toRadians(lat))
                val d = dLat * dLat + dLng * dLng
                if (d < bestD) { bestD = d; best = p }
            }
        }
        return best
    }
}

// ==================== Data Models ====================

enum class EventType(val label: String) {
    LANE_CHANGE_LEFT("向左变道"), LANE_CHANGE_RIGHT("向右变道"),
    HARD_BRAKING("急刹车"), RAPID_ACCELERATION("急加速"),
    SHARP_TURN_LEFT("左急转弯"), SHARP_TURN_RIGHT("右急转弯")
}

data class DataPoint(
    val timestamp: Long, val latitude: Double, val longitude: Double,
    val speedKmh: Float, val heading: Float, val altitude: Double,
    val accuracy: Float, val latAccel: Float, val lonAccel: Float
)

data class DrivingEvent(
    val timestamp: Long, val eventType: EventType,
    val latitude: Double, val longitude: Double,
    val speedKmh: Float, val heading: Float, val severity: Float, val description: String
)

// ==================== Simulated Drive Engine ====================

class SimulatedDriveEngine {
    // 武汉高速大范围路线（模拟长距离行驶）
    private val route = listOf(
        30.91474 to 114.04569, 30.91550 to 114.04500, 30.91630 to 114.04430,
        30.91720 to 114.04360, 30.91850 to 114.04300, 30.92000 to 114.04250,
        30.92200 to 114.04180, 30.92450 to 114.04100, 30.92700 to 114.04050,
        30.93000 to 114.04000, 30.93300 to 114.03950, 30.93600 to 114.03880,
        30.93900 to 114.03800, 30.94200 to 114.03750, 30.94500 to 114.03700,
        30.94800 to 114.03650, 30.95100 to 114.03600, 30.95400 to 114.03550,
        30.95700 to 114.03500, 30.96000 to 114.03450, 30.96300 to 114.03400,
        30.96600 to 114.03350, 30.96900 to 114.03300, 30.97200 to 114.03250,
        30.97500 to 114.03200, 30.97800 to 114.03150, 30.98100 to 114.03080,
        30.98400 to 114.03000, 30.98700 to 114.02950, 30.99000 to 114.02900,
        30.99300 to 114.02850, 30.99600 to 114.02800, 31.00000 to 114.02750,
        31.00500 to 114.02700, 31.01000 to 114.02650, 31.01500 to 114.02600,
        31.02000 to 114.02580, 31.02500 to 114.02560, 31.03000 to 114.02540,
        31.03500 to 114.02520, 31.04000 to 114.02500, 31.04500 to 114.02480,
        31.05000 to 114.02460, 31.05500 to 114.02440, 31.06000 to 114.02420,
        31.06500 to 114.02400, 31.07000 to 114.02380, 31.07500 to 114.02360,
        31.08000 to 114.02340, 31.08500 to 114.02320, 31.09000 to 114.02300,
        31.09500 to 114.02280, 31.10000 to 114.02260, 31.10500 to 114.02240,
        31.11000 to 114.02220, 31.11500 to 114.02200, 31.11550 to 114.02180
    )
    private var routeIndex = 0; private var subStep = 0f
    var speedKmh = 40f; private set
    var heading = 180f; private set
    var latitude = route[0].first; private set
    var longitude = route[0].second; private set
    var altitude = 52.0; private set
    var latAccel = 0f; private set
    var lonAccel = 0f; private set
    var totalDistance = 0f; private set
    private var targetSpeed = 45f
    private var laneChangeActive = false; private var laneChangeTimer = 0L
    private var brakeActive = false; private var accelActive = false; private var turnActive = false
    private var actionTimer = 0L; private var lastEventTime = 0L

    fun tick(): List<DrivingEvent> {
        val now = System.currentTimeMillis(); val events = mutableListOf<DrivingEvent>()
        if (abs(speedKmh - targetSpeed) < 1f) targetSpeed = (30..70).random().toFloat()
        speedKmh += (targetSpeed - speedKmh) * 0.05f
        val r = KRandom.nextFloat()

        if (!laneChangeActive && !brakeActive && !turnActive && r < 0.003f && speedKmh > 25f) {
            laneChangeActive = true; laneChangeTimer = now
            val d = if (KRandom.nextBoolean()) EventType.LANE_CHANGE_RIGHT else EventType.LANE_CHANGE_LEFT
            val sev = 0.3f + KRandom.nextFloat() * 0.7f
            events.add(DrivingEvent(now, d, latitude, longitude, speedKmh, heading, sev,
                "${d.label} (横向: ${String.format("%.1f", 1.5f + sev * 2f)} m/s²)"))
            lastEventTime = now
        }
        if (!brakeActive && !laneChangeActive && r < 0.001f && speedKmh > 40f) {
            brakeActive = true; actionTimer = now
            targetSpeed = maxOf(10f, speedKmh - (15..35).random())
            val sev = 0.4f + KRandom.nextFloat() * 0.6f
            events.add(DrivingEvent(now, EventType.HARD_BRAKING, latitude, longitude, speedKmh, heading, sev,
                "急刹车: 减速度 ${String.format("%.1f", 3.5f + sev * 3f)} m/s²"))
            lastEventTime = now
        }
        if (!accelActive && !brakeActive && r < 0.001f && speedKmh < 50f) {
            accelActive = true; actionTimer = now
            targetSpeed = minOf(80f, speedKmh + (15..35).random())
            val sev = 0.3f + KRandom.nextFloat() * 0.7f
            events.add(DrivingEvent(now, EventType.RAPID_ACCELERATION, latitude, longitude, speedKmh, heading, sev,
                "急加速: 加速度 ${String.format("%.1f", 3.5f + sev * 2f)} m/s²"))
            lastEventTime = now
        }
        if (!turnActive && !laneChangeActive && r < 0.002f && speedKmh > 20f) {
            turnActive = true; actionTimer = now
            val th = heading + if (KRandom.nextBoolean()) KRandom.nextFloat() * 40f + 30f else -KRandom.nextFloat() * 40f - 30f
            heading = ((th % 360 + 360) % 360)
            val d = if (KRandom.nextBoolean()) EventType.SHARP_TURN_RIGHT else EventType.SHARP_TURN_LEFT
            val sev = 0.4f + KRandom.nextFloat() * 0.6f
            events.add(DrivingEvent(now, d, latitude, longitude, speedKmh, heading, sev,
                "急转弯: 航向变化 ${String.format("%.0f", abs(heading - th))}°"))
            lastEventTime = now
        }

        latAccel = when { laneChangeActive -> (if (KRandom.nextBoolean()) 1.8f else -1.8f) + KRandom.nextFloat() * 0.5f
            turnActive -> (if (KRandom.nextBoolean()) 3f else -3f) + KRandom.nextFloat() * 1f
            else -> (KRandom.nextFloat() - 0.5f) * 0.4f }
        lonAccel = when { brakeActive -> -4f - KRandom.nextFloat() * 2f
            accelActive -> 4f + KRandom.nextFloat() * 2f
            else -> (targetSpeed - speedKmh) * 0.3f }

        if (laneChangeActive && now - laneChangeTimer > 1500 + KRandom.nextLong(1000)) laneChangeActive = false
        if (brakeActive && now - actionTimer > 1000 + KRandom.nextLong(500)) brakeActive = false
        if (accelActive && now - actionTimer > 1000 + KRandom.nextLong(500)) accelActive = false
        if (turnActive && now - actionTimer > 2000 + KRandom.nextLong(1000)) turnActive = false

        subStep += (speedKmh / 3.6f) * 0.2f / 100f
        if (subStep >= 1f) { subStep = 0f; routeIndex = (routeIndex + 1) % route.size }
        val (lat1, lon1) = route[routeIndex]
        val (lat2, lon2) = route[(routeIndex + 1) % route.size]
        latitude = lat1 + (lat2 - lat1) * subStep
        longitude = lon1 + (lon2 - lon1) * subStep
        heading = ((bearing(lat1, lon1, lat2, lon2) + (KRandom.nextFloat() - 0.5f) * 5f + 360) % 360)
        altitude = 52.0 + sin(routeIndex * 0.3) * 15.0
        totalDistance += (speedKmh / 3.6f) * 0.2f
        return events
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dL = Math.toRadians(lon2 - lon1)
        val rL1 = Math.toRadians(lat1); val rL2 = Math.toRadians(lat2)
        return Math.toDegrees(atan2(sin(dL) * cos(rL2), cos(rL1) * sin(rL2) - sin(rL1) * cos(rL2) * cos(dL))).toFloat()
    }

    fun reset() {
        routeIndex = 0; subStep = 0f; speedKmh = 40f; targetSpeed = 45f
        heading = 180f; totalDistance = 0f
        latitude = route[0].first; longitude = route[0].second
        laneChangeActive = false; brakeActive = false; accelActive = false; turnActive = false
    }
}

// ==================== Satellite Map Engine ====================

class TileCache {
    private val cache = mutableMapOf<String, ImageBitmap?>()
    private val loading = mutableSetOf<String>()

    fun getTile(z: Int, x: Int, y: Int, onLoaded: () -> Unit): ImageBitmap? {
        val key = "$z/$x/$y"
        if (key in cache) return cache[key]
        if (key in loading) return null
        loading.add(key)
        Thread {
            try {
                val url = URL("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x")
                val conn = url.openConnection()
                conn.setRequestProperty("User-Agent", "DrivingRecorder/1.0")
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val img = ImageIO.read(conn.getInputStream())
                cache[key] = if (img != null) img.toComposeImageBitmap() else null
            } catch (_: Exception) { cache[key] = null }
            loading.remove(key)
            onLoaded()
        }.start()
        return null
    }

    fun clear() { cache.clear(); loading.clear() }
}

// Web Mercator projection utilities
object Mercator {
    const val TILE_SIZE = 256.0
    fun latLngToTile(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val x = (lng + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return x to y
    }

    fun tileToPixel(tileX: Double, tileY: Double): Pair<Double, Double> {
        return (tileX % 1.0) * TILE_SIZE to (tileY % 1.0) * TILE_SIZE
    }
}

// ==================== Speed Gauge ====================

@Composable
fun SpeedGauge(speedKmh: Float, maxSpeed: Float = 160f, modifier: Modifier = Modifier) {
    val normalized = (speedKmh / maxSpeed).coerceIn(0f, 1f)
    val gaugeColor = when {
        normalized < 0.5f -> Color(0xFF4CAF50)
        normalized < 0.75f -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.10f; val pad = stroke / 2
            val arcSz = androidx.compose.ui.geometry.Size(size.minDimension - pad * 2, size.minDimension - pad * 2)
            drawArc(Color.White.copy(alpha = 0.25f), 135f, 270f, false, Offset(pad, pad), arcSz,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(gaugeColor, 135f, normalized * 270f, false, Offset(pad, pad), arcSz,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${speedKmh.toInt()}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("km/h", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ==================== Map View ====================

@Composable
fun SatelliteMapView(
    centerLat: Double, centerLng: Double, heading: Float,
    trackPoints: List<Pair<Double, Double>>,
    events: List<DrivingEvent>,
    deviceMarkers: List<DeviceMarker> = emptyList(),
    modifier: Modifier = Modifier
) {
    val tileCache = remember { TileCache() }
    var zoom by remember { mutableStateOf(15) }
    var refresh by remember { mutableStateOf(0) }
    // Auto-zoom based on speed (simulated)
    LaunchedEffect(Unit) { zoom = 15 }

    Canvas(modifier = modifier.pointerInput(Unit) {}) {
        val w = size.width; val h = size.height

        // Calculate center tile
        val (cx, cy) = Mercator.latLngToTile(centerLat, centerLng, zoom)
        val (px, py) = Mercator.tileToPixel(cx, cy)

        val tileSize = Mercator.TILE_SIZE.toFloat()
        val tilesX = (w / tileSize).toInt() + 2
        val tilesY = (h / tileSize).toInt() + 2
        val baseX = cx.toInt() - tilesX / 2
        val baseY = cy.toInt() - tilesY / 2

        // Background (ocean color for areas without tiles)
        drawRect(Color(0xFF1A2332))

        // Draw tiles
        for (tx in 0 until tilesX) {
            for (ty in 0 until tilesY) {
                val tileX = baseX + tx; val tileY = baseY + ty
                val maxT = (1 shl zoom) - 1
                if (tileX < 0 || tileY < 0 || tileX > maxT || tileY > maxT) continue

                val screenX = (tx - tilesX / 2) * tileSize + (tileSize - px.toFloat())
                val screenY = (ty - tilesY / 2) * tileSize + (tileSize - py.toFloat())

                val bmp = tileCache.getTile(zoom, tileX, tileY) { refresh++ }
                if (bmp != null) {
                    drawImage(bmp,
                        dstOffset = androidx.compose.ui.unit.IntOffset(screenX.toInt(), screenY.toInt()),
                        dstSize = IntSize(tileSize.toInt(), tileSize.toInt()))
                } else {
                    // Loading placeholder
                    drawRect(Color(0xFF2A3342), topLeft = Offset(screenX, screenY),
                        size = androidx.compose.ui.geometry.Size(tileSize, tileSize))
                }
            }
        }

        // Draw track line
        if (trackPoints.size >= 2) {
            val path = Path()
            var first = true
            for ((lat, lng) in trackPoints) {
                val (tx, ty) = Mercator.latLngToTile(lat, lng, zoom)
                val sx = (tx - cx) * tileSize + w / 2
                val sy = (ty - cy) * tileSize + h / 2
                if (first) { path.moveTo(sx.toFloat(), sy.toFloat()); first = false }
                else path.lineTo(sx.toFloat(), sy.toFloat())
            }
            drawPath(path, Color(0xFF00E5FF), style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Draw event markers
        for (e in events.takeLast(50)) {
            val (tx, ty) = Mercator.latLngToTile(e.latitude, e.longitude, zoom)
            val sx = (tx - cx) * tileSize + w / 2
            val sy = (ty - cy) * tileSize + h / 2
            val color = when (e.eventType) {
                EventType.LANE_CHANGE_LEFT, EventType.LANE_CHANGE_RIGHT -> Color(0xFFFF9800)
                EventType.HARD_BRAKING -> Color(0xFFE53935)
                EventType.RAPID_ACCELERATION -> Color(0xFFFF6D00)
                EventType.SHARP_TURN_LEFT, EventType.SHARP_TURN_RIGHT -> Color(0xFF7C4DFF)
            }
            drawCircle(color, 5f, Offset(sx.toFloat(), sy.toFloat()))
            drawCircle(Color.White, 2f, Offset(sx.toFloat(), sy.toFloat()))
        }

        // Draw device markers with labels
        for (dm in deviceMarkers) {
            val (dtx, dty) = Mercator.latLngToTile(dm.lat, dm.lng, zoom)
            val dsx = (dtx - cx) * tileSize + w / 2
            val dsy = (dty - cy) * tileSize + h / 2

            // 颜色区分：卡口=品红, 雷视=青色
            val isKakou = dm.type == "卡口"
            val devColor = if (isKakou) Color(0xFFFF00FF) else Color(0xFF00E5FF)  // magenta vs cyan
            val radius = 9f

            // 发光背景
            drawCircle(devColor.copy(alpha = 0.3f), radius + 6f, Offset(dsx.toFloat(), dsy.toFloat()))
            // 主圆点
            drawCircle(devColor, radius, Offset(dsx.toFloat(), dsy.toFloat()))
            // 白色中心
            drawCircle(Color.White, 3f, Offset(dsx.toFloat(), dsy.toFloat()))

            // 外圈白色描边让标记更显眼
            drawCircle(Color.White.copy(alpha = 0.5f), radius + 2f, Offset(dsx.toFloat(), dsy.toFloat()),
                style = Stroke(1.5f))
        }

        // Draw current position (blue dot with heading arrow)
        val cxScreen = w / 2; val cyScreen = h / 2
        drawCircle(Color(0xFF448AFF).copy(alpha = 0.3f), 18f, Offset(cxScreen, cyScreen))
        drawCircle(Color.White, 8f, Offset(cxScreen, cyScreen))
        drawCircle(Color(0xFF2979FF), 6f, Offset(cxScreen, cyScreen))

        // Heading arrow
        val hdgRad = Math.toRadians(heading.toDouble() - 90.0) // adjust for screen coordinates
        val arrowLen = 18f
        val tipX = cxScreen + arrowLen * cos(hdgRad).toFloat()
        val tipY = cyScreen + arrowLen * sin(hdgRad).toFloat()
        drawCircle(Color.White, 3f, Offset(tipX, tipY))
    }
}

// ==================== Main App ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingRecorderDesktop() {
    val engine = remember { SimulatedDriveEngine() }
    val roadIndex = remember { DesktopRoadIndex().also { it.loadFromResource("3-XG-ABCDK_locations_supplement.json") } }
    val leishiMarkers = remember { loadLeishiDevices(roadIndex) }
    val kakouMarkers = remember { loadCheckpointDevices() }
    val allDeviceMarkers = remember { leishiMarkers + kakouMarkers }
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var allEvents by remember { mutableStateOf(listOf<DrivingEvent>()) }
    var allPoints by remember { mutableStateOf(listOf<DataPoint>()) }
    var currentTab by remember { mutableStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                delay(200)
                if (!isRecording) break
                val events = engine.tick()
                elapsed += 200
                allEvents = (events + allEvents).take(100)
                allPoints = (listOf(DataPoint(
                    System.currentTimeMillis(), engine.latitude, engine.longitude,
                    engine.speedKmh, engine.heading, engine.altitude, 3.5f,
                    engine.latAccel, engine.lonAccel
                )) + allPoints).take(1000)
            }
        }
    }

    fun startRecording() { engine.reset(); allEvents = emptyList(); allPoints = emptyList(); elapsed = 0; isRecording = true }
    fun stopRecording() { isRecording = false }

    fun exportCSV() {
        val c = JFileChooser(); c.dialogTitle = "导出 CSV"; c.fileFilter = FileNameExtensionFilter("CSV", "csv")
        c.selectedFile = File("driving_data.csv")
        if (c.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val f = c.selectedFile; val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            f.writeText("﻿timestamp,latitude,longitude,speed_kmh,heading,altitude,accuracy,lat_accel,lon_accel\n")
            allPoints.reversed().forEach { p -> f.appendText("${sdf.format(Date(p.timestamp))},${p.latitude},${p.longitude},${"%.1f".format(p.speedKmh)},${"%.1f".format(p.heading)},${"%.1f".format(p.altitude)},${p.accuracy},${"%.3f".format(p.latAccel)},${"%.3f".format(p.lonAccel)}\n") }
            val ef = File(f.parent, f.nameWithoutExtension + "_events.csv")
            ef.writeText("﻿timestamp,event_type,latitude,longitude,speed_kmh,severity,description\n")
            allEvents.forEach { e -> ef.appendText("${sdf.format(Date(e.timestamp))},${e.eventType.label},${e.latitude},${e.longitude},${"%.1f".format(e.speedKmh)},${"%.2f".format(e.severity)},${e.description}\n") }
        }
    }

    fun exportJSON() {
        val c = JFileChooser(); c.dialogTitle = "导出 JSON"; c.fileFilter = FileNameExtensionFilter("JSON", "json")
        c.selectedFile = File("driving_data.json")
        if (c.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val f = c.selectedFile; val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            val json = buildString {
                appendLine("{"); appendLine("  \"trip\": {")
                appendLine("    \"durationSec\": ${elapsed / 1000},")
                appendLine("    \"totalDistanceM\": ${"%.1f".format(engine.totalDistance)},")
                appendLine("    \"maxSpeedKmh\": ${"%.1f".format(allPoints.maxOfOrNull { it.speedKmh } ?: 0f)},")
                appendLine("    \"pointCount\": ${allPoints.size},\n    \"eventCount\": ${allEvents.size}")
                appendLine("  },\n  \"dataPoints\": [")
                allPoints.reversed().forEachIndexed { i, p ->
                    appendLine("    {\"ts\":\"${sdf.format(Date(p.timestamp))}\",\"lat\":${p.latitude},\"lng\":${p.longitude},\"spd\":${"%.1f".format(p.speedKmh)},\"hdg\":${"%.1f".format(p.heading)}}${if (i < allPoints.lastIndex) "," else ""}")
                }
                appendLine("  ],\n  \"events\": [")
                allEvents.forEachIndexed { i, e ->
                    appendLine("    {\"ts\":\"${sdf.format(Date(e.timestamp))}\",\"type\":\"${e.eventType.name}\",\"spd\":${"%.1f".format(e.speedKmh)},\"sev\":${"%.2f".format(e.severity)}}${if (i < allEvents.lastIndex) "," else ""}")
                }
                appendLine("  ]\n}")
            }
            f.writeText(json)
        }
    }

    // APK 同款布局：地图全屏 + 悬浮按钮
    Box(Modifier.fillMaxSize()) {
        // 地图
        SatelliteMapView(
            centerLat = engine.latitude, centerLng = engine.longitude,
            heading = engine.heading,
            trackPoints = allPoints.reversed().take(500).map { it.latitude to it.longitude },
            events = allEvents,
            deviceMarkers = allDeviceMarkers,
            modifier = Modifier.fillMaxSize()
        )

        // 右上角：坐标 + 设备图例
        val leiC = allDeviceMarkers.count { it.type == "雷视" }
        val kaC = allDeviceMarkers.count { it.type == "卡口" }
        Column(Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.65f)).padding(8.dp)) {
            Text(String.format("%.6f", engine.latitude), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            Text(String.format("%.6f", engine.longitude), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            if (leiC > 0) Text("🩵雷视:${leiC}", fontSize = 10.sp, color = Color(0xFF00E5FF))
            if (kaC > 0) Text("💗卡口:${kaC}", fontSize = 10.sp, color = Color(0xFFFF00FF))
        }

        if (isRecording) {
            // 录制中：左上速度+桩号 + 底部信息 + 停止按钮
            val roadPt = roadIndex.queryNearest(engine.latitude, engine.longitude)
            Column(Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(10.dp)) {
                Text("${engine.speedKmh.toInt()}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("km/h · 航向${"%.0f".format(engine.heading)}°", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                roadPt?.let { pt ->
                    Spacer(Modifier.height(4.dp))
                    val rampTxt = if (pt.ramp.isNotEmpty()) " · ${pt.ramp}匝" else ""
                    Text("${pt.location}$rampTxt", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE53935).copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 5.dp)) {
                Text("● 录制中 ${formatElapsed(elapsed)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f)).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("${"%.2f".format(engine.totalDistance / 1000f)}km", color = Color.White, fontSize = 12.sp)
                Text("${allPoints.size}点", color = Color.White, fontSize = 12.sp)
                Text("${allEvents.size}事件", color = Color.White, fontSize = 12.sp)
            }

            FloatingActionButton(
                onClick = { stopRecording() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = Color(0xFFE53935)
            ) { Icon(Icons.Default.Stop, "停止", tint = Color.White) }

        } else {
            // 未录制：居中开始按钮 + 导出按钮
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.7f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("驾驶记录仪", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(16.dp))
                FloatingActionButton(
                    onClick = { startRecording() },
                    containerColor = Color(0xFF1A73E8), modifier = Modifier.size(72.dp)
                ) { Icon(Icons.Default.PlayArrow, "开始", tint = Color.White, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.height(12.dp))
                Text("开始记录", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (allPoints.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = ::exportCSV) { Text("导出CSV", color = Color(0xFF4CAF50)) }
                        TextButton(onClick = ::exportJSON) { Text("导出JSON", color = Color(0xFF2196F3)) }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${String.format("%02d", s % 60)}"
}
// ==================== Map Tab ====================

@Composable
fun MapTab(
    padding: PaddingValues, engine: SimulatedDriveEngine, isRecording: Boolean,
    allPoints: List<DataPoint>, allEvents: List<DrivingEvent>, elapsed: Long,
    onStart: () -> Unit, onStop: () -> Unit, roadIndex: DesktopRoadIndex,
    deviceMarkers: List<DeviceMarker> = emptyList()
) {
    // 查询道路桩号
    val roadPt = remember(engine.latitude, engine.longitude) {
        roadIndex.queryNearest(engine.latitude, engine.longitude)
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
        // Map
        SatelliteMapView(
            centerLat = engine.latitude, centerLng = engine.longitude,
            heading = engine.heading,
            trackPoints = allPoints.reversed().take(500).map { it.latitude to it.longitude },
            events = allEvents,
            deviceMarkers = deviceMarkers,
            modifier = Modifier.fillMaxSize()
        )

        // Speed + 桩号 overlay (top-left)
        Column(
            Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(12.dp)
        ) {
            Text("${engine.speedKmh.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("km/h", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
            Text("航向 ${"%.0f".format(engine.heading)}°", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            roadPt?.let { pt ->
                Spacer(Modifier.height(6.dp))
                val d = kotlin.math.sqrt(
                    (pt.lat - engine.latitude) * 111320.0 * (pt.lat - engine.latitude) * 111320.0 +
                    (pt.lng - engine.longitude) * 111320.0 * cos(Math.toRadians(engine.latitude)) *
                    (pt.lng - engine.longitude) * 111320.0 * cos(Math.toRadians(engine.latitude))
                )
                val rampTxt = if (pt.ramp.isNotEmpty()) " · ${pt.ramp}匝" else ""
                Text("${pt.location}$rampTxt",
                    fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                if (d < 100) Text("距中心${"%.0f".format(d)}m",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
            } ?: Text("道路索引加载中...", fontSize = 10.sp, color = Color(0xFFFF9800))
        }

        // Legend + info overlay (top-right)
        val leiShiCount = deviceMarkers.count { it.type == "雷视" }
        val kaKouCount = deviceMarkers.count { it.type == "卡口" }
        Column(
            Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(10.dp)
                .align(Alignment.TopEnd)
        ) {
            Text(String.format("%.6f", engine.latitude), fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Text(String.format("%.6f", engine.longitude), fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Text("WGS84", fontSize = 10.sp, color = Color(0xFF4CAF50))
            Spacer(Modifier.height(4.dp))
            if (leiShiCount > 0) Text("🩵 雷视: ${leiShiCount}台", fontSize = 10.sp, color = Color(0xFF00E5FF))
            if (kaKouCount > 0) Text("💗 卡口: ${kaKouCount}个", fontSize = 10.sp, color = Color(0xFFFF00FF))
        }

        // Recording indicator (top-center)
        if (isRecording) {
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE53935).copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 4.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text("● 录制中 ${elapsed / 1000 / 60}:${String.format("%02d", elapsed / 1000 % 60)}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Stats bar (bottom)
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format("%.2f", engine.totalDistance / 1000f), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("距离(km)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${allPoints.size}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("数据点", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${allEvents.size}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("事件", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("5Hz", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("采集率", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }

        // Start/Stop FAB
        FloatingActionButton(
            onClick = { if (isRecording) onStop() else onStart() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = if (isRecording) Color(0xFFE53935) else Color(0xFF1A73E8)
        ) {
            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                "录制", tint = Color.White)
        }

        if (!isRecording) {
            Text("点击 ▶ 开始模拟驾驶",
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f)).padding(12.dp),
                color = Color.White, fontSize = 16.sp)
        }
    }
}

// ==================== Dashboard Tab ====================

@Composable
fun DashboardTab(padding: PaddingValues, engine: SimulatedDriveEngine, isRecording: Boolean, elapsed: Long, onStart: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(180.dp).background(Color(0xFF1A1A2E), RoundedCornerShape(90.dp)), contentAlignment = Alignment.Center) {
            SpeedGauge(speedKmh = if (isRecording) engine.speedKmh else 0f, modifier = Modifier.size(160.dp))
        }
        Spacer(Modifier.height(12.dp))

        if (isRecording) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("● 录制中", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${elapsed / 1000 / 60}:${String.format("%02d", elapsed / 1000 % 60)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("时长", fontSize = 11.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.2f", engine.totalDistance / 1000f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("距离(km)", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("纬度", color = Color.Gray, fontSize = 13.sp); Text(String.format("%.6f", engine.latitude), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("经度", color = Color.Gray, fontSize = 13.sp); Text(String.format("%.6f", engine.longitude), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("航向角", color = Color.Gray, fontSize = 13.sp); Text("${"%.0f".format(if (isRecording) engine.heading else 0f)}°", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("海拔", color = Color.Gray, fontSize = 13.sp); Text("${"%.1f".format(engine.altitude)}m", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("横向加速度", color = Color.Gray, fontSize = 13.sp); Text("${"%.3f".format(if (isRecording) engine.latAccel else 0f)} m/s²", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("纵向加速度", color = Color.Gray, fontSize = 13.sp); Text("${"%.3f".format(if (isRecording) engine.lonAccel else 0f)} m/s²", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (isRecording) onStop() else onStart() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFE53935) else Color(0xFF1A73E8))
        ) {
            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (isRecording) "停止记录" else "开始记录（模拟驾驶）", color = Color.White)
        }
    }
}

// ==================== Events Tab ====================

@Composable
fun EventsTab(padding: PaddingValues, events: List<DrivingEvent>) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("暂无驾驶事件\n开始录制后将自动检测", color = Color.Gray)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(events) { event ->
                Card {
                    Row(Modifier.padding(10.dp)) {
                        Surface(Modifier.size(10.dp), shape = RoundedCornerShape(2.dp),
                            color = when (event.eventType) {
                                EventType.LANE_CHANGE_LEFT, EventType.LANE_CHANGE_RIGHT -> Color(0xFFFF9800)
                                EventType.HARD_BRAKING -> Color(0xFFE53935)
                                EventType.RAPID_ACCELERATION -> Color(0xFFFF6D00)
                                EventType.SHARP_TURN_LEFT, EventType.SHARP_TURN_RIGHT -> Color(0xFF7C4DFF)
                            }) {}
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.eventType.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${"%.0f".format(event.speedKmh)} km/h | 严重 ${"%.0f".format(event.severity * 100)}%", fontSize = 12.sp, color = Color.Gray)
                                Text(SimpleDateFormat("HH:mm:ss").format(Date(event.timestamp)), fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(event.description, fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Export Tab ====================

@Composable
fun ExportTab(padding: PaddingValues, isRecording: Boolean, points: List<DataPoint>, events: List<DrivingEvent>, elapsed: Long, engine: SimulatedDriveEngine, onCSV: () -> Unit, onJSON: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text("导出数据", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("行程摘要", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("录制时长", color = Color.Gray, fontSize = 13.sp); Text("${elapsed / 1000 / 60}分${elapsed / 1000 % 60}秒", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("总距离", color = Color.Gray, fontSize = 13.sp); Text(String.format("%.2f km", engine.totalDistance / 1000f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("数据点 (5Hz)", color = Color.Gray, fontSize = 13.sp); Text("${points.size} 条", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("驾驶事件", color = Color.Gray, fontSize = 13.sp); Text("${events.size} 个", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCSV, Modifier.fillMaxWidth().height(48.dp), enabled = !isRecording, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
            Icon(Icons.Default.TableChart, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("导出 CSV（Excel 兼容）", color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onJSON, Modifier.fillMaxWidth().height(48.dp), enabled = !isRecording, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
            Icon(Icons.Default.Code, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("导出 JSON（结构化数据）", color = Color.White)
        }
        if (isRecording) { Spacer(Modifier.height(12.dp)); Text("请先停止录制再导出", color = Color(0xFFE53935), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        Spacer(Modifier.weight(1f))
        Text("桌面版使用模拟GPS数据 + ESRI卫星影像 | 真机APK使用真实GPS/传感器", fontSize = 10.sp, color = Color.Gray, lineHeight = 14.sp)
    }
}
