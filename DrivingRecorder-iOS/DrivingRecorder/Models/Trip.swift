import Foundation

/// 行程汇总
struct Trip: Codable, Identifiable {
    let id: UUID
    let startTime: Date
    var endTime: Date?
    var maxSpeedKmh: Double
    var avgSpeedKmh: Double
    var totalDistanceM: Double
    var pointCount: Int
    var eventCount: Int

    var durationSec: TimeInterval {
        guard let end = endTime else { return Date().timeIntervalSince(startTime) }
        return end.timeIntervalSince(startTime)
    }

    init(
        id: UUID = UUID(),
        startTime: Date = Date(),
        endTime: Date? = nil,
        maxSpeedKmh: Double = 0,
        avgSpeedKmh: Double = 0,
        totalDistanceM: Double = 0,
        pointCount: Int = 0,
        eventCount: Int = 0
    ) {
        self.id = id
        self.startTime = startTime
        self.endTime = endTime
        self.maxSpeedKmh = maxSpeedKmh
        self.avgSpeedKmh = avgSpeedKmh
        self.totalDistanceM = totalDistanceM
        self.pointCount = pointCount
        self.eventCount = eventCount
    }
}
