package com.drivingrecorder.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.ui.components.DashboardCard
import com.drivingrecorder.ui.components.DataRow
import com.drivingrecorder.ui.components.DataRowDual
import com.drivingrecorder.ui.components.EventTimeline
import com.drivingrecorder.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    navController: NavController,
    tripId: Long,
    viewModel: TripDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as DrivingRecorderApp
    val trip by viewModel.trip.collectAsState()
    val events by viewModel.events.collectAsState()

    LaunchedEffect(tripId) {
        viewModel.loadTrip(app, tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("行程详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    trip?.let {
                        IconButton(onClick = {
                            navController.navigate("export/${it.id}")
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "导出",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        trip?.let { t ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 行程概览
                item {
                    DashboardCard {
                        Text(
                            text = "行程概览",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DataRow("日期", DateTimeUtils.toDateString(t.startTime))
                        DataRow(
                            "时间",
                            "${DateTimeUtils.toTimeString(t.startTime)} - ${t.endTime?.let { DateTimeUtils.toTimeString(it) } ?: "进行中"}"
                        )
                        DataRow("时长", DateTimeUtils.formatDurationChinese(t.durationMs / 1000))
                    }
                }

                // 行驶数据
                item {
                    DashboardCard {
                        Text(
                            text = "行驶数据",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DataRowDual(
                            "总距离", "%.2f km".format(t.totalDistance / 1000f),
                            "数据点", "${t.pointCount}"
                        )
                        DataRowDual(
                            "最高速度", "%.0f km/h".format(t.maxSpeed),
                            "平均速度", "%.0f km/h".format(t.avgSpeed)
                        )
                    }
                }

                // 事件列表
                item {
                    Text(
                        text = "驾驶事件 (${events.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    EventTimeline(events = events)
                }

                // 底部操作按钮
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { navController.navigate("export/${t.id}") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导出数据")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteTrip(app, t.id)
                                navController.popBackStack()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("删除")
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
