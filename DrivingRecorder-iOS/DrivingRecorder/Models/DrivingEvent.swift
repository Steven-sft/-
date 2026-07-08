import Foundation

/// 驾驶行为事件
struct DrivingEvent: Codable, Identifiable {
    let id: UUID
    let tripId: UUID
    let timestamp: Date
    let eventType: EventType
    let latitude: Double
    let longitude: Double
    let speedKmh: Double
    let heading: Double
    let severity: Double       // 0.0 - 1.0
    let description: String

    init(
        id: UUID = UUID(),
        tripId: UUID,
        timestamp: Date = Date(),
        eventType: EventType,
        latitude: Double,
        longitude: Double,
        speedKmh: Double,
        heading: Double,
        severity: Double,
        description: String
    ) {
        self.id = id
        self.tripId = tripId
        self.timestamp = timestamp
        self.eventType = eventType
        self.latitude = latitude
        self.longitude = longitude
        self.speedKmh = speedKmh
        self.heading = heading
        self.severity = severity
        self.description = description
    }
}
