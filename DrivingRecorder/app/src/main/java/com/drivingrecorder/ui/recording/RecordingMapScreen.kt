package com.drivingrecorder.ui.recording

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.data.repository.TripRepository
import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType
import com.drivingrecorder.util.DateTimeUtils
import kotlinx.coroutines.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay

// ESRI 卫星影像瓦片源
val SATELLITE_TILE_SOURCE = XYTileSource(
    "ESRI Satellite", 1, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© ESRI"
)

@Composable
fun RecordingMapScreen(
    navController: NavController,
    tripId: Long,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as DrivingRecorderApp
    val isRecording by app.recordingRepository.isRecording.collectAsState()
    val currentSpeed by app.recordingRepository.currentSpeed.collectAsState()
    val currentHeading by app.recordingRepository.currentHeading.collectAsState()
    val elapsed by app.recordingRepository.elapsedSeconds.collectAsState()
    val totalDistance by app.recordingRepository.totalDistance.collectAsState()
    val recentEvents by app.recordingRepository.recentEvents.collectAsState()
    val latestPoint by app.recordingRepository.latestDataPoint.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var trackLine by remember { mutableStateOf<Polyline?>(null) }
    var posMarker by remember { mutableStateOf<Marker?>(null) }
    val eventMarkers = remember { mutableListOf<Marker>() }

    // 加载历史轨迹点
    var historyPoints by remember { mutableStateOf<List<DataPoint>>(emptyList()) }
    LaunchedEffect(tripId) {
        withContext(Dispatchers.IO) {
            historyPoints = app.tripRepository.getDataPoints(tripId)
        }
    }

    // 实时更新地图
    LaunchedEffect(latestPoint) {
        latestPoint?.let { point ->
            mapView?.let { map ->
                val geoPoint = GeoPoint(point.latitude, point.longitude)

                // 更新轨迹线
                val allPoints = historyPoints + listOf(point)
                if (allPoints.size >= 2) {
                    trackLine?.setPoints(allPoints.map { GeoPoint(it.latitude, it.longitude) })
                }

                // 更新位置标记
                posMarker?.position = geoPoint
                posMarker?.rotation = -point.heading // osmdroid 旋转方向

                // 地图跟随
                map.controller.animateTo(geoPoint, map.zoomLevelDouble, 500L)
            }
        }
    }

    // 更新事件标记
    LaunchedEffect(recentEvents.size) {
        mapView?.let { map ->
            // 清除旧标记
            eventMarkers.forEach { map.overlays.remove(it) }
            eventMarkers.clear()

            // 添加新事件标记
            recentEvents.take(20).forEach { event ->
                val marker = Marker(map).apply {
                    position = GeoPoint(event.latitude, event.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = event.eventType.displayName
                    snippet = "${"%.0f".format(event.speed)} km/h | ${event.description}"
                }
                map.overlays.add(marker)
                eventMarkers.add(marker)
            }
            map.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 地图
        AndroidView(
            factory = { ctx ->
                // 配置 osmdroid
                org.osmdroid.config.Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    osmdroidBasePath = ctx.cacheDir
                    osmdroidTileCache = ctx.cacheDir.resolve("tiles")
                }

                MapView(ctx).apply {
                    // 使用卫星瓦片
                    setTileSource(SATELLITE_TILE_SOURCE)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    minZoomLevel = 3.0
                    maxZoomLevel = 19.0
                    controller.setZoom(17.0)

                    // 比例尺
                    val scaleBar = ScaleBarOverlay(this)
                    scaleBar.setAlignBottom(true)
                    scaleBar.setAlignRight(true)
                    overlays.add(scaleBar)

                    // 轨迹线
                    val polyline = Polyline().apply {
                        outlinePaint.apply {
                            color = android.graphics.Color.rgb(0, 229, 255)
                            strokeWidth = 8f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                    }
                    overlays.add(polyline)
                    trackLine = polyline

                    // 当前位置标记
                    val marker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = null // 用 Compose 绘制
                    }
                    overlays.add(marker)
                    posMarker = marker

                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 速度悬浮面板（左上）
        Column(
            Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(10.dp)
        ) {
            Text(
                "${currentSpeed.toInt()}",
                fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text("km/h", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            Text(
                "航向 ${"%.0f".format(currentHeading)}°",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)
            )
        }

        // 录制状态（顶部居中）
        if (isRecording) {
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE53935).copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    "● 录制中 ${DateTimeUtils.formatDuration(elapsed)}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // 底部统计条
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatText("${"%.2f".format(totalDistance / 1000f)}", "距离(km)")
            StatText("${historyPoints.size + 1}", "数据点")
            StatText("${recentEvents.size}", "事件")
            StatText("5Hz", "采集率", Color(0xFF4CAF50))
        }

        // 停止按钮
        FloatingActionButton(
            onClick = {
                onStop()
                navController.popBackStack()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFE53935)
        ) {
            Icon(Icons.Default.Stop, "停止", tint = Color.White)
        }
    }

    // 停止录制时退出
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            navController.popBackStack()
        }
    }
}

@Composable
private fun StatText(value: String, label: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}
