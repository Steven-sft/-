import Foundation
import Combine

/// 录制服务：编排 GPS + 传感器 + 检测 + 存储
@MainActor
final class RecordingService: ObservableObject {
    @Published var isRecording = false
    @Published var currentTripId: UUID?
    @Published var currentSpeed: Double = 0
    @Published var currentHeading: Double = 0
    @Published var currentLatitude: Double = 0
    @Published var currentLongitude: Double = 0
    @Published var currentAltitude: Double = 0
    @Published var lateralAccel: Double = 0
    @Published var longitudinalAccel: Double = 0
    @Published var totalDistance: Double = 0
    @Published var elapsedSeconds: TimeInterval = 0
    @Published var recentEvents: [DrivingEvent] = []
    @Published var pointCount: Int = 0

    private let locationService = LocationService()
    private let motionService = MotionService()
    private let detectionEngine = DetectionEngine()
    private let dataStore = DataStore.shared

    private var startTime: Date = .distantPast
    private var timer: Timer?
    private var previousLocation: (lat: Double, lon: Double)?
    private var cancellables = Set<AnyCancellable>()

    // 数据点缓冲区（高频批量写入）
    private var pointBuffer: [DataPoint] = []
    private var lastBufferFlush: Date = .distantPast

    init() {
        setupBindings()
    }

    private func setupBindings() {
        // 监听位置更新
        locationService.$currentLocation
            .compactMap { $0 }
            .sink { [weak self] loc in
                guard let self = self, self.isRecording, let tripId = self.currentTripId else { return }
                self.onLocationUpdate(loc, tripId: tripId)
            }
            .store(in: &cancellables)

        // 监听传感器
        motionService.$lateralAccel.combineLatest(motionService.$longitudinalAccel)
            .sink { [weak self] lat, lon in
                self?.lateralAccel = lat
                self?.longitudinalAccel = lon
            }
            .store(in: &cancellables)
    }

    private func onLocationUpdate(_ location: CLLocation, tripId: UUID) {
        let speedKmh = max(0, MathUtils.msToKmh(location.speed))
        let heading = location.course >= 0 ? location.course : 0.0
        let lat = location.coordinate.latitude
        let lon = location.coordinate.longitude

        currentSpeed = speedKmh
        currentHeading = heading
        currentLatitude = lat
        currentLongitude = lon
        currentAltitude = location.altitude

        // 累计距离
        if let prev = previousLocation {
            totalDistance += MathUtils.haversineDistance(
                lat1: prev.lat, lon1: prev.lon, lat2: lat, lon2: lon
            )
        }
        previousLocation = (lat, lon)

        // 创建数据点
        let point = DataPoint(
            tripId: tripId, timestamp: Date(),
            latitude: lat, longitude: lon,
            speedKmh: speedKmh, heading: heading,
            accuracy: location.horizontalAccuracy,
            altitude: location.altitude,
            lateralAccel: lateralAccel,
            longitudinalAccel: longitudinalAccel
        )

        pointBuffer.append(point)
        pointCount += 1

        // 批量写入（每 500ms 或 20 个点）
        let now = Date()
        if pointBuffer.count >= 20 || now.timeIntervalSince(lastBufferFlush) >= 0.5 {
            flushBuffer()
            lastBufferFlush = now
        }

        // 运行检测引擎
        let events = detectionEngine.process(point: point)
        for event in events {
            recentEvents.insert(event, at: 0)
            if recentEvents.count > 100 { recentEvents.removeLast() }
            Task {
                await dataStore.saveEvent(event)
                try? await Task.sleep(nanoseconds: 0)
            }
        }
    }

    private func flushBuffer() {
        guard !pointBuffer.isEmpty else { return }
        let batch = pointBuffer
        pointBuffer.removeAll()
        Task {
            await dataStore.saveDataPoints(batch)
        }
    }

    // MARK: - 控制
    func startRecording() async -> Trip {
        locationService.requestPermission()
        // 等待权限（简化处理）
        try? await Task.sleep(nanoseconds: 500_000_000)

        locationService.startUpdating()
        motionService.startUpdating()

        let trip = Trip(startTime: Date())
        await dataStore.saveTrip(trip)

        currentTripId = trip.id
        startTime = Date()
        isRecording = true
        totalDistance = 0
        elapsedSeconds = 0
        recentEvents = []
        pointCount = 0
        pointBuffer.removeAll()
        lastBufferFlush = Date()
        previousLocation = nil
        detectionEngine.reset()

        // 时间计时器
        timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self = self, self.isRecording else { return }
                self.elapsedSeconds = Date().timeIntervalSince(self.startTime)
            }
        }

        // 初始事件
        let startEvent = DrivingEvent(
            tripId: trip.id, eventType: .tripStart,
            latitude: 0, longitude: 0, speedKmh: 0, heading: 0,
            severity: 0, description: "行程开始"
        )
        recentEvents.append(startEvent)
        Task { await dataStore.saveEvent(startEvent) }

        return trip
    }

    func stopRecording() async -> Trip? {
        isRecording = false
        timer?.invalidate(); timer = nil
        locationService.stopUpdating()
        motionService.stopUpdating()
        flushBuffer()

        guard let tripId = currentTripId else { return nil }

        // 汇总行程
        let endTime = Date()
        let allPoints = await dataStore.getDataPoints(for: tripId)
        let allEvents = await dataStore.getEvents(for: tripId)

        let maxSpeed = allPoints.map(\.speedKmh).max() ?? 0
        let avgSpeed = allPoints.isEmpty ? 0 : allPoints.map(\.speedKmh).reduce(0, +) / Double(allPoints.count)

        let endEvent = DrivingEvent(
            tripId: tripId, eventType: .tripEnd,
            latitude: currentLatitude, longitude: currentLongitude,
            speedKmh: 0, heading: 0, severity: 0, description: "行程结束"
        )
        recentEvents.append(endEvent)
        await dataStore.saveEvent(endEvent)

        let updated = Trip(
            id: tripId, startTime: startTime, endTime: endTime,
            maxSpeedKmh: maxSpeed, avgSpeedKmh: avgSpeed,
            totalDistanceM: totalDistance,
            pointCount: allPoints.count, eventCount: allEvents.count
        )
        await dataStore.updateTrip(updated)

        currentTripId = nil
        return updated
    }
}

// CLLocation import for type reference
import CoreLocation
