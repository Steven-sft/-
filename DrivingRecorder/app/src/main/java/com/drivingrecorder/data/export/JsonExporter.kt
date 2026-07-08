package com.drivingrecorder.data.export

import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.util.DateTimeUtils
import com.drivingrecorder.util.FileUtils
import java.io.File

/**
 * JSON 格式导出器
 * 生成单个 JSON 文件，包含行程汇总、全部数据点和事件
 */
class JsonExporter {

    suspend fun export(app: DrivingRecorderApp, tripId: Long): List<File> {
        val trip = app.tripRepository.getTrip(tripId) ?: return emptyList()
        val points = app.tripRepository.getDataPoints(tripId)
        val events = app.tripRepository.getEvents(tripId)

        val jsonFile = FileUtils.createTempFile(
            app,
            "trip_${tripId}_export.json"
        )

        val json = buildString {
            appendLine("{")
            appendLine("  \"trip\": {")
            appendLine("    \"id\": ${trip.id},")
            appendLine("    \"startTime\": \"${DateTimeUtils.toIsoString(trip.startTime)}\",")
            trip.endTime?.let {
                appendLine("    \"endTime\": \"${DateTimeUtils.toIsoString(it)}\",")
            }
            appendLine("    \"maxSpeedKmh\": ${"%.1f".format(trip.maxSpeed)},")
            appendLine("    \"avgSpeedKmh\": ${"%.1f".format(trip.avgSpeed)},")
            appendLine("    \"totalDistanceM\": ${"%.1f".format(trip.totalDistance)},")
            appendLine("    \"durationSec\": ${trip.durationMs / 1000},")
            appendLine("    \"pointCount\": ${trip.pointCount},")
            appendLine("    \"eventCount\": ${trip.eventCount}")
            appendLine("  },")

            // 数据点数组
            appendLine("  \"dataPoints\": [")
            points.forEachIndexed { index, point ->
                val comma = if (index < points.lastIndex) "," else ""
                appendLine("    {" +
                        "\"timestamp\": ${point.timestamp}, " +
                        "\"lat\": ${point.latitude}, " +
                        "\"lng\": ${point.longitude}, " +
                        "\"speedKmh\": ${"%.1f".format(point.speed)}, " +
                        "\"heading\": ${"%.1f".format(point.heading)}, " +
                        "\"altitude\": ${"%.1f".format(point.altitude)}, " +
                        "\"accuracy\": ${"%.1f".format(point.accuracy)}, " +
                        "\"latAccel\": ${"%.3f".format(point.lateralAccel)}, " +
                        "\"lonAccel\": ${"%.3f".format(point.longitudinalAccel)}" +
                        "}$comma")
            }
            appendLine("  ],")

            // 事件数组
            appendLine("  \"events\": [")
            events.forEachIndexed { index, event ->
                val comma = if (index < events.lastIndex) "," else ""
                val escapedDesc = event.description
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                appendLine("    {" +
                        "\"timestamp\": ${event.timestamp}, " +
                        "\"type\": \"${event.eventType.name}\", " +
                        "\"displayName\": \"${event.eventType.displayName}\", " +
                        "\"lat\": ${event.latitude}, " +
                        "\"lng\": ${event.longitude}, " +
                        "\"speedKmh\": ${"%.1f".format(event.speed)}, " +
                        "\"heading\": ${"%.1f".format(event.heading)}, " +
                        "\"severity\": ${"%.2f".format(event.severity)}, " +
                        "\"description\": \"$escapedDesc\"" +
                        "}$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }

        FileUtils.writeText(jsonFile, json)
        return listOf(jsonFile)
    }
}
