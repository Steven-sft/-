import Foundation

/// 单次 GPS + 传感器采集数据点
struct DataPoint: Codable, Identifiable {
    let id: UUID
    let tripId: UUID
    let timestamp: Date
    let latitude: Double
    let longitude: Double
    let speedKmh: Double       // km/h
    let heading: Double        // 度 0-360
    let accuracy: Double       // 米
    let altitude: Double       // 米
    let lateralAccel: Double   // m/s² 横向
    let longitudinalAccel: Double // m/s² 纵向

    init(
        id: UUID = UUID(),
        tripId: UUID,
        timestamp: Date = Date(),
        latitude: Double,
        longitude: Double,
        speedKmh: Double,
        heading: Double,
        accuracy: Double,
        altitude: Double,
        lateralAccel: Double,
        longitudinalAccel: Double
    ) {
        self.id = id
        self.tripId = tripId
        self.timestamp = timestamp
        self.latitude = latitude
        self.longitude = longitude
        self.speedKmh = speedKmh
        self.heading = heading
        self.accuracy = accuracy
        self.altitude = altitude
        self.lateralAccel = lateralAccel
        self.longitudinalAccel = longitudinalAccel
    }
}
