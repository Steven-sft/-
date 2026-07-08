package com.drivingrecorder.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.drivingrecorder.DrivingRecorderApp
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File

private val SATELLITE_SOURCE = object : OnlineTileSourceBase(
    "ESRI_Satellite", 1, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(tile: Long) =
        baseUrl + MapTileIndex.getZoom(tile) + "/" + MapTileIndex.getY(tile) + "/" + MapTileIndex.getX(tile) + mImageFilenameEnding
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMapScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as DrivingRecorderApp
    val isRecording by app.recordingRepository.isRecording.collectAsState()
    val trips by viewModel.trips.collectAsState()

    var mapView by remember { mutableStateOf<MapView?>(null) }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startRecording(context, navController)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadTrips(app)
    }

    fun doStartRecording() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.startRecording(context, navController)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("驾驶记录仪") },
                actions = {
                    IconButton(onClick = { navController.navigate("history") }) {
                        Icon(Icons.Default.List, "历史记录", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A73E8),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                        setTileSource(SATELLITE_SOURCE)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        minZoomLevel = 2.0; maxZoomLevel = 19.0
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(30.9147, 114.0457))

                        val scaleBar = ScaleBarOverlay(this).apply {
                            setAlignBottom(true); setAlignRight(true); setLineWidth(3f)
                        }
                        overlays.add(scaleBar)

                        mapView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ===== 开始按钮（居中大按钮）=====
            if (!isRecording) {
                Column(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.7f)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("驾驶记录仪", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("点击开始记录行驶数据", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(20.dp))
                    FloatingActionButton(
                        onClick = { doStartRecording() },
                        containerColor = Color(0xFF1A73E8),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, "开始记录", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("开始记录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // ===== 最近行程提示 =====
            if (!isRecording) {
                trips.firstOrNull()?.let { trip ->
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("上次行程", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                "${com.drivingrecorder.util.DateTimeUtils.toDateString(trip.startTime)} · ${com.drivingrecorder.util.DateTimeUtils.formatDuration(trip.durationMs / 1000)}",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(onClick = { navController.navigate("detail/${trip.id}") }) {
                            Text("详情")
                        }
                    }
                }
            }
        }
    }
}
