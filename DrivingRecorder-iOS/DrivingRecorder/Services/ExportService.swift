import Foundation
import UIKit

/// 数据导出服务
enum ExportService {
    // MARK: - CSV 导出
    static func exportCSV(trip: Trip, points: [DataPoint], events: [DrivingEvent]) -> URL? {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        // 数据点 CSV
        var csv = "\u{FEFF}timestamp,latitude,longitude,speed_kmh,heading,altitude,accuracy,lat_accel,lon_accel\n"
        for p in points {
            csv += "\(Formatters.dateTimeFormatter.string(from: p.timestamp)),\(p.latitude),\(p.longitude),"
            csv += "\(String(format: "%.1f", p.speedKmh)),\(String(format: "%.1f", p.heading)),"
            csv += "\(String(format: "%.1f", p.altitude)),\(String(format: "%.1f", p.accuracy)),"
            csv += "\(String(format: "%.3f", p.lateralAccel)),\(String(format: "%.3f", p.longitudinalAccel))\n"
        }

        let csvFile = dir.appendingPathComponent("trip_data.csv")
        try? csv.write(to: csvFile, atomically: true, encoding: .utf8)

        // 事件 CSV
        var eventsCsv = "\u{FEFF}timestamp,event_type,latitude,longitude,speed_kmh,severity,description\n"
        for e in events {
            let desc = e.description.replacingOccurrences(of: ",", with: "，")
            eventsCsv += "\(Formatters.dateTimeFormatter.string(from: e.timestamp)),\(e.eventType.displayName),"
            eventsCsv += "\(e.latitude),\(e.longitude),\(String(format: "%.1f", e.speedKmh)),"
            eventsCsv += "\(String(format: "%.2f", e.severity)),\(desc)\n"
        }

        let evFile = dir.appendingPathComponent("trip_events.csv")
        try? eventsCsv.write(to: evFile, atomically: true, encoding: .utf8)

        return csvFile
    }

    // MARK: - JSON 导出
    static func exportJSON(trip: Trip, points: [DataPoint], events: [DrivingEvent]) -> URL? {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        struct ExportData: Codable {
            let trip: Trip
            let dataPoints: [DataPoint]
            let events: [DrivingEvent]
        }

        let export = ExportData(trip: trip, dataPoints: points, events: events)
        let jsonFile = dir.appendingPathComponent("trip_export.json")
        if let data = try? encoder.encode(export) {
            try? data.write(to: jsonFile)
            return jsonFile
        }
        return nil
    }

    // MARK: - 系统分享
    @MainActor
    static func share(fileURL: URL, from viewController: UIViewController) {
        let activityVC = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = viewController.view
        }
        viewController.present(activityVC, animated: true)
    }
}
