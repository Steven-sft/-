import Foundation

/// 本地数据存储（基于 JSON 文件，简洁稳定）
/// 数据量不大时 JSON 文件读写足够高效
actor DataStore {
    static let shared = DataStore()

    private let fileManager = FileManager.default
    private let documentsDir: URL

    private var trips: [UUID: Trip] = [:]
    private var dataPoints: [UUID: [DataPoint]] = [:]   // tripId → points
    private var events: [UUID: [DrivingEvent]] = [:]     // tripId → events

    private init() {
        documentsDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("DrivingRecorder")
        try? fileManager.createDirectory(at: documentsDir, withIntermediateDirectories: true)
        Task { await loadAll() }
    }

    // MARK: - Trip
    func saveTrip(_ trip: Trip) {
        trips[trip.id] = trip
        persist(filename: "trips.json", data: trips.values.map { $0 })
    }

    func updateTrip(_ trip: Trip) {
        trips[trip.id] = trip
        persist(filename: "trips.json", data: trips.values.map { $0 })
    }

    func getTrip(id: UUID) -> Trip? { trips[id] }

    func getAllTrips() -> [Trip] {
        trips.values.sorted { $0.startTime > $1.startTime }
    }

    func deleteTrip(id: UUID) {
        trips.removeValue(forKey: id)
        dataPoints.removeValue(forKey: id)
        events.removeValue(forKey: id)
        persist(filename: "trips.json", data: trips.values.map { $0 })
        // 清理对应的数据文件
        let dpFile = fileURL("points_\(id.uuidString).json")
        let evFile = fileURL("events_\(id.uuidString).json")
        try? fileManager.removeItem(at: dpFile)
        try? fileManager.removeItem(at: evFile)
    }

    // MARK: - DataPoints
    func saveDataPoints(_ points: [DataPoint]) {
        guard let tripId = points.first?.tripId else { return }
        var existing = dataPoints[tripId] ?? []
        existing.append(contentsOf: points)
        dataPoints[tripId] = existing
        persist(filename: "points_\(tripId.uuidString).json", data: existing)
    }

    func getDataPoints(for tripId: UUID) -> [DataPoint] {
        dataPoints[tripId] ?? []
    }

    // MARK: - Events
    func saveEvent(_ event: DrivingEvent) {
        var existing = events[event.tripId] ?? []
        existing.append(event)
        events[event.tripId] = existing
        persist(filename: "events_\(event.tripId.uuidString).json", data: existing)
    }

    func getEvents(for tripId: UUID) -> [DrivingEvent] {
        events[tripId] ?? []
    }

    // MARK: - Persistence
    private func fileURL(_ filename: String) -> URL {
        documentsDir.appendingPathComponent(filename)
    }

    private func persist<T: Encodable>(filename: String, data: T) {
        let url = fileURL(filename)
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            let json = try encoder.encode(data)
            try json.write(to: url)
        } catch {
            print("DataStore persist error: \(error)")
        }
    }

    private func loadAll() {
        // 加载行程
        if let tripData = try? Data(contentsOf: fileURL("trips.json")),
           let loaded = try? JSONDecoder().decode([Trip].self, from: tripData) {
            trips = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0) })
        }

        // 加载每个行程的数据点和事件
        for trip in trips.values {
            if let dpData = try? Data(contentsOf: fileURL("points_\(trip.id.uuidString).json")),
               let loaded = try? JSONDecoder().decode([DataPoint].self, from: dpData) {
                dataPoints[trip.id] = loaded
            }
            if let evData = try? Data(contentsOf: fileURL("events_\(trip.id.uuidString).json")),
               let loaded = try? JSONDecoder().decode([DrivingEvent].self, from: evData) {
                events[trip.id] = loaded
            }
        }
    }
}
