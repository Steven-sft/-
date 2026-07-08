package com.drivingrecorder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivingrecorder.domain.model.DrivingEvent
import com.drivingrecorder.domain.model.EventType
import com.drivingrecorder.ui.theme.*
import com.drivingrecorder.util.DateTimeUtils

/**
 * 事件时间线组件
 */
@Composable
fun EventTimeline(
    events: List<DrivingEvent>,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "本次行程无异常驾驶事件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            EventRow(event = event, isLast = index == events.lastIndex)
        }
    }
}

@Composable
private fun EventRow(event: DrivingEvent, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 时间线指示器
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // 圆点
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.small,
                color = eventColor(event.eventType)
            ) {}
            // 连接线
            if (!isLast) {
                Surface(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                ) {}
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 事件内容
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.eventType.displayName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = eventColor(event.eventType)
                    )
                    Text(
                        text = DateTimeUtils.toTimeString(event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${"%.0f".format(event.speed)} km/h | 严重程度: ${"%.0f".format(event.severity * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun eventColor(type: EventType): androidx.compose.ui.graphics.Color = when (type) {
    EventType.LANE_CHANGE_LEFT, EventType.LANE_CHANGE_RIGHT -> Warning
    EventType.HARD_BRAKING -> RecordingRed
    EventType.RAPID_ACCELERATION -> androidx.compose.ui.graphics.Color(0xFFFF6D00)
    EventType.SHARP_TURN_LEFT, EventType.SHARP_TURN_RIGHT -> androidx.compose.ui.graphics.Color(0xFF7C4DFF)
    else -> Success
}
