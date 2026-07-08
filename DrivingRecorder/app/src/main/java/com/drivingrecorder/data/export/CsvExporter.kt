package com.drivingrecorder.data.export

import com.drivingrecorder.DrivingRecorderApp
import com.drivingrecorder.util.FileUtils
import java.io.File

/**
 * CSV 格式导出器
 * 生成两个文件：data_points.csv 和 driving_events.csv
 */
class CsvExporter {

    suspend fun export(app: DrivingRecorderApp, tripId: Long): List<File> {
        val trip = app.tripRepository.getTrip(tripId) ?: return emptyList()
        val points = app.tripRepository.getDataPoints(tripId)
        val events = app.tripRepository.getEvents(tripId)

        val files = mutableListOf<File>()

        // 导出数据点 CSV
        if (points.isNotEmpty()) {
            val pointsFile = FileUtils.createTempFile(
                app,
                "trip_${tripId}_data_points.csv"
            )

            FileUtils.writeBom(pointsFile)

            // CSV 表头
            val header = "timestamp,latitude,longitude,speed_kmh,heading,altitude,accuracy," +
                    "lateral_accel_ms2,longitudinal_accel_ms2\n"
            FileUtils.writeText(pointsFile, header)

            // 每行数据
            val rows = points.joinToString("\n") { point ->
                "${point.timestamp},${point.latitude},${point.longitude}," +
                        "${"%.1f".format(point.speed)},${"%.1f".format(point.heading)}," +
                        "${"%.1f".format(point.altitude)},${"%.1f".format(point.accuracy)}," +
                        "${"%.3f".format(point.lateralAccel)},${"%.3f".format(point.longitudinalAccel)}"
            }
            FileUtils.writeText(pointsFile, rows, append = true)

            files.add(pointsFile)
        }

        // 导出驾驶事件 CSV
        if (events.isNotEmpty()) {
            val eventsFile = FileUtils.createTempFile(
                app,
                "trip_${tripId}_driving_events.csv"
            )

            FileUtils.writeBom(eventsFile)

            val header = "timestamp,event_type,latitude,longitude,speed_kmh,heading," +
                    "severity,description\n"
            FileUtils.writeText(eventsFile, header)

            val rows = events.joinToString("\n") { event ->
                val desc = event.description.replace(",", "，")  // 替换逗号避免CSV冲突
                "${event.timestamp},${event.eventType.displayName},${event.latitude}," +
                        "${event.longitude},${"%.1f".format(event.speed)}," +
                        "${"%.1f".format(event.heading)},${"%.2f".format(event.severity)},${desc}"
            }
            FileUtils.writeText(eventsFile, rows, append = true)

            files.add(eventsFile)
        }

        return files
    }
}
