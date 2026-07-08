package com.drivingrecorder.ui.export

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.ui.components.DashboardCard
import com.drivingrecorder.ui.components.DataRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    tripId: Long,
    viewModel: ExportViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as DrivingRecorderApp
    val trip by viewModel.trip.collectAsState()
    var selectedFormat by remember { mutableStateOf("csv") }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        viewModel.loadTrip(app, tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出数据") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 行程摘要
            trip?.let { t ->
                DashboardCard {
                    Text(
                        text = "导出行程",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DataRow("总距离", "%.2f km".format(t.totalDistance / 1000f))
                    DataRow("数据点", "${t.pointCount} 条")
                    DataRow("事件", "${t.eventCount} 个")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 格式选择
            Text(
                text = "选择导出格式",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            // CSV 选项
            FormatOption(
                title = "CSV 格式",
                subtitle = "表格数据，可用 Excel 打开分析。包含完整数据点和驾驶事件两个文件。",
                icon = Icons.Default.TableChart,
                selected = selectedFormat == "csv",
                onClick = { selectedFormat = "csv" }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // JSON 选项
            FormatOption(
                title = "JSON 格式",
                subtitle = "结构化数据，适合程序化处理。包含行程汇总、全部数据点和事件。",
                icon = Icons.Default.Code,
                selected = selectedFormat == "json",
                onClick = { selectedFormat = "json" }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 导出按钮
            Button(
                onClick = {
                    isExporting = true
                    viewModel.export(
                        app = app,
                        tripId = tripId,
                        format = selectedFormat,
                        onComplete = { files ->
                            isExporting = false
                            if (files.isNotEmpty()) {
                                // 分享第一个文件
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        files.first()
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                        type = if (selectedFormat == "csv") "text/csv" else "application/json"
                                        putExtra(Intent.EXTRA_STREAM, files.map {
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                it
                                            )
                                        }.toArrayList())
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "导出驾驶数据")
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "导出成功，文件已保存",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "导出失败：无数据",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isExporting && trip != null
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出并分享", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormatOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// 扩展函数：List<File> → ArrayList<Uri>
private fun <T> List<T>.toArrayList(): java.util.ArrayList<T> {
    return java.util.ArrayList(this)
}
