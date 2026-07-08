package com.drivingrecorder.ui.recording

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
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
import com.drivingrecorder.domain.model.DataPoint
import com.drivingrecorder.util.DateTimeUtils
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File

// ==================== 多种底图源 ====================

data class MapLayer(
    val name: String,
    val icon: String,
    val source: OnlineTileSourceBase
)

// ESRI 卫星影像
private val ESRI_SATELLITE = object : OnlineTileSourceBase(
    "ESRI_Satellite", 1, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(tile: Long) =
        baseUrl + MapTileIndex.getZoom(tile) + "/" + MapTileIndex.getY(tile) + "/" + MapTileIndex.getX(tile) + mImageFilenameEnding
}

// ESRI 街道图
private val ESRI_STREET = object : OnlineTileSourceBase(
    "ESRI_Street", 1, 19, 256, ".png",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/")
) {
    override fun getTileURLString(tile: Long) =
        baseUrl + MapTileIndex.getZoom(tile) + "/" + MapTileIndex.getY(tile) + "/" + MapTileIndex.getX(tile) + mImageFilenameEnding
}

// ESRI 地形图
private val ESRI_TOPO = object : OnlineTileSourceBase(
    "ESRI_Topo", 1, 17, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/")
) {
    override fun getTileURLString(tile: Long) =
        baseUrl + MapTileIndex.getZoom(tile) + "/" + MapTileIndex.getY(tile) + "/" + MapTileIndex.getX(tile) + mImageFilenameEnding
}

// CartoDB 浅色底图（简洁现代）
private val CARTODB_LIGHT = object : OnlineTileSourceBase(
    "CartoDB_Positron", 1, 19, 256, ".png",
    arrayOf("https://basemaps.cartocdn.com/light_all/")
) {
    override fun getTileURLString(tile: Long) =
        baseUrl + MapTileIndex.getZoom(tile) + "/" + MapTileIndex.getX(tile) + "/" + MapTileIndex.getY(tile) + mImageFilenameEnding
}

// CartoDB 深色底图
private val CARTODB_DARK = object : OnlineTileSourceBase(
    "CartoDB_DarkMatter", 1, 19, 256, ".png",
    arrayOf("https://basemaps.cartocdn.com/dark_all/")
) {
    override fun getTileURLString(tile: Long) =
        baseUrl + MapTileIndex.getZoom(tile) + "/" + MapTileIndex.getX(tile) + "/" + MapTileIndex.getY(tile) + mImageFilenameEnding
}

private val ALL_LAYERS = listOf(
    MapLayer("卫星影像",    "🛰️", ESRI_SATELLITE),
    MapLayer("街道地图",    "🗺️", TileSourceFactory.MAPNIK),
    MapLayer("ESRI 街道",  "🏙️", ESRI_STREET),
    MapLayer("ESRI 地形",  "⛰️", ESRI_TOPO),
    MapLayer("浅色简洁",    "⬜", CARTODB_LIGHT),
    MapLayer("深色酷炫",    "⬛", CARTODB_DARK),
)

// ==================== 组件 ====================

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

    var mapInitialized by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var trackLine by remember { mutableStateOf<Polyline?>(null) }
    var posMarker by remember { mutableStateOf<Marker?>(null) }
    val eventMarkers = remember { mutableListOf<Marker>() }
    var historyPoints by remember { mutableStateOf<List<DataPoint>>(emptyList()) }

    var currentLayerIndex by remember { mutableIntStateOf(0) }
    var showLayerPicker by remember { mutableStateOf(false) }

    // 加载历史轨迹
    LaunchedEffect(tripId) {
        withContext(Dispatchers.IO) {
            historyPoints = app.tripRepository.getDataPoints(tripId)
        }
    }

    // 实时更新轨迹和位置
    LaunchedEffect(latestPoint) {
        latestPoint?.let { point ->
            mapView?.let { map ->
                val geoPoint = GeoPoint(point.latitude, point.longitude)
                val allPoints = historyPoints + listOf(point)
                if (allPoints.size >= 2) {
                    trackLine?.setPoints(allPoints.map { GeoPoint(it.latitude, it.longitude) })
                }
                posMarker?.position = geoPoint
                posMarker?.rotation = -point.heading
                if (mapInitialized) {
                    map.controller.animateTo(geoPoint, map.zoomLevelDouble, 500L)
                }
            }
        }
    }

    // 更新事件标记
    LaunchedEffect(recentEvents.size) {
        mapView?.let { map ->
            eventMarkers.forEach { map.overlays.remove(it) }; eventMarkers.clear()
            recentEvents.take(20).forEach { event ->
                val marker = Marker(map).apply {
                    position = GeoPoint(event.latitude, event.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = event.eventType.displayName
                }
                map.overlays.add(marker); eventMarkers.add(marker)
            }
            map.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 地图 =====
        AndroidView(
            factory = { ctx ->
                val osmdroidDir = File(ctx.cacheDir, "osmdroid")
                if (!osmdroidDir.exists()) osmdroidDir.mkdirs()
                val tileCacheDir = File(osmdroidDir, "tiles")
                if (!tileCacheDir.exists()) tileCacheDir.mkdirs()

                Configuration.getInstance().apply {
                    userAgentValue = "DrivingRecorder/1.0"
                    osmdroidBasePath = osmdroidDir
                    osmdroidTileCache = tileCacheDir
                }

                MapView(ctx).apply {
                    setTileSource(ALL_LAYERS[currentLayerIndex].source)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    minZoomLevel = 2.0; maxZoomLevel = 19.0
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(39.9139, 116.4105))

                    val scaleBar = ScaleBarOverlay(this).apply {
                        setAlignBottom(true); setAlignRight(true); setLineWidth(3f)
                    }
                    overlays.add(scaleBar)

                    val polyline = Polyline().apply {
                        outlinePaint.apply {
                            color = android.graphics.Color.rgb(0, 210, 255)
                            strokeWidth = 8f; style = Paint.Style.STROKE
                            isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                        }
                    }
                    overlays.add(polyline); trackLine = polyline

                    val m = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "当前位置"
                    }
                    overlays.add(m); posMarker = m

                    mapView = this; mapInitialized = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ===== 速度面板（左上）=====
        Column(
            Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(10.dp)
        ) {
            Text("${currentSpeed.toInt()}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("km/h", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Text("航向 ${"%.0f".format(currentHeading)}°", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        }

        // ===== 当前底图名称（右上）=====
        Text(
            "${ALL_LAYERS[currentLayerIndex].icon} ${ALL_LAYERS[currentLayerIndex].name}",
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(8.dp),
            color = Color.White, fontSize = 11.sp
        )

        // ===== 录制状态（顶部居中）=====
        if (isRecording) {
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE53935).copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 5.dp).align(Alignment.TopCenter)
            ) {
                Text("● 录制中 ${DateTimeUtils.formatDuration(elapsed)}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ===== 底图切换按钮（左下）=====
        Column(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 底图选择器弹窗
            if (showLayerPicker) {
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A2E).copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .width(160.dp)
                ) {
                    ALL_LAYERS.forEachIndexed { index, layer ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                currentLayerIndex = index
                                mapView?.setTileSource(ALL_LAYERS[index].source)
                                showLayerPicker = false
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(layer.icon, fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(layer.name,
                                color = if (index == currentLayerIndex) Color(0xFF4CAF50) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (index == currentLayerIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 切换按钮
            FloatingActionButton(
                onClick = { showLayerPicker = !showLayerPicker },
                containerColor = Color(0xFF1A73E8),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Layers, "换底图", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        // ===== 底部统计条 =====
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f)).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatText("${"%.2f".format(totalDistance / 1000f)}", "距离(km)")
            StatText("${historyPoints.size + 1}", "数据点")
            StatText("${recentEvents.size}", "事件")
            StatText("5Hz", "采集率", Color(0xFF4CAF50))
        }

        // ===== 停止按钮（右下）=====
        FloatingActionButton(
            onClick = { onStop(); navController.popBackStack() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Color(0xFFE53935)
        ) { Icon(Icons.Default.Stop, "停止", tint = Color.White) }
    }

    LaunchedEffect(isRecording) { if (!isRecording) navController.popBackStack() }
}

@Composable
private fun StatText(value: String, label: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}
