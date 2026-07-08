package com.drivingrecorder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivingrecorder.ui.theme.*

/**
 * 速度表盘组件
 * 圆弧形速度显示，带动态颜色变化
 */
@Composable
fun SpeedGauge(
    speedKmh: Float,
    maxSpeed: Float = 160f,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val normalizedSpeed = (speedKmh / maxSpeed).coerceIn(0f, 1f)

    // 颜色根据速度动态变化：绿→黄→红
    val gaugeColor = when {
        normalizedSpeed < 0.5f -> Success          // 0-50%
        normalizedSpeed < 0.75f -> Warning          // 50-75%
        else -> RecordingRed                        // 75%+
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.toPx() * 0.12f
            val arcPadding = strokeWidth / 2
            val arcSize = Size(
                size.toPx() - arcPadding * 2,
                size.toPx() - arcPadding * 2
            )
            val arcTopLeft = Offset(arcPadding, arcPadding)

            // 背景圆弧
            drawArc(
                color = Color.DarkGray.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 前景圆弧（速度指示）
            val sweepAngle = normalizedSpeed * 270f
            drawArc(
                color = gaugeColor,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 刻度线
            val numTicks = 8
            for (i in 0..numTicks) {
                val angle = Math.toRadians((135f + i * 270f / numTicks).toDouble())
                val innerRadius = size.toPx() * 0.30f
                val outerRadius = size.toPx() * 0.36f
                val cx = size.toPx() / 2
                val cy = size.toPx() / 2
                val startX = cx + innerRadius * Math.cos(angle).toFloat()
                val startY = cy + innerRadius * Math.sin(angle).toFloat()
                val endX = cx + outerRadius * Math.cos(angle).toFloat()
                val endY = cy + outerRadius * Math.sin(angle).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
            }
        }

        // 中心速度数字
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${speedKmh.toInt()}",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 52.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
