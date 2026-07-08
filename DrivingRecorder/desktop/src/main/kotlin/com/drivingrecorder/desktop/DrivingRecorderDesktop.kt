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
    private val route = listOf(
        39.9139 to 116.4105, 39.9148 to 116.4096, 39.9157 to 116.4087,
        39.9165 to 116.4078, 39.9170 to 116.4065, 39.9172 to 116.4052,
        39.9170 to 116.4039, 39.9165 to 116.4026, 39.9158 to 116.4015,
        39.9148 to 116.4008, 39.9138 to 116.4002, 39.9128 to 116.3998,
        39.9118 to 116.3996, 39.9108 to 116.3998, 39.9098 to 116.4003,
        39.9090 to 116.4010, 39.9085 to 116.4020, 39.9082 to 116.4030,
        39.9080 to 116.4040, 39.9082 to 116.4050, 39.9088 to 116.4058,
        39.9095 to 116.4062, 39.9105 to 116.4062, 39.9115 to 116.4058,
        39.9125 to 116.4050, 39.9132 to 116.4040, 39.9135 to 116.4030
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

        // Draw current position (blue dot with heading arrow)
        val cxScreen = w / 2; val cyScreen = h / 2
        // Pulse ring
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("驾驶记录仪") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A73E8), titleContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Map, "地图") }, label = { Text("轨迹地图") })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Dashboard, "数据") }, label = { Text("仪表盘") })
                NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Timeline, "事件") }, label = { Text("事件(${allEvents.size})") })
                NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Share, "导出") }, label = { Text("导出") })
            }
        }
    ) { padding ->
        when (currentTab) {
            0 -> MapTab(padding, engine, isRecording, allPoints, allEvents, elapsed, ::startRecording, ::stopRecording)
            1 -> DashboardTab(padding, engine, isRecording, elapsed, ::startRecording, ::stopRecording)
            2 -> EventsTab(padding, allEvents)
            3 -> ExportTab(padding, isRecording, allPoints, allEvents, elapsed, engine, ::exportCSV, ::exportJSON)
        }
    }
}

// ==================== Map Tab ====================

@Composable
fun MapTab(
    padding: PaddingValues, engine: SimulatedDriveEngine, isRecording: Boolean,
    allPoints: List<DataPoint>, allEvents: List<DrivingEvent>, elapsed: Long,
    onStart: () -> Unit, onStop: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        // Map
        SatelliteMapView(
            centerLat = engine.latitude, centerLng = engine.longitude,
            heading = engine.heading,
            trackPoints = allPoints.reversed().take(500).map { it.latitude to it.longitude },
            events = allEvents,
            modifier = Modifier.fillMaxSize()
        )

        // Speed overlay (top-left)
        Column(
            Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(12.dp)
        ) {
            Text("${engine.speedKmh.toInt()}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("km/h", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
            Text("航向 ${"%.0f".format(engine.heading)}°", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }

        // Info overlay (top-right)
        Column(
            Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(10.dp)
                .align(Alignment.TopEnd)
        ) {
            Text(String.format("%.6f", engine.latitude), fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Text(String.format("%.6f", engine.longitude), fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Text("卫星影像", fontSize = 10.sp, color = Color(0xFF4CAF50))
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
