import Foundation

/// 驾驶行为检测引擎
/// 融合 GPS 航向角 + 加速度计数据检测变道、急刹、急转弯、急加速
final class DetectionEngine {
    // MARK: - 检测阈值配置
    struct Config {
        var laneChangeLatAccelMin: Double = 1.5    // m/s² (~0.15g)
        var laneChangeMinDuration: TimeInterval = 0.8
        var laneChangeMaxDuration: TimeInterval = 3.5
        var laneChangeHeadingMin: Double = 1.5       // 度
        var laneChangeHeadingMax: Double = 8.0       // 度
        var laneChangeMinSpeed: Double = 15           // km/h
        var hardBrakingDecelMin: Double = -3.5       // m/s²
        var hardBrakingSpeedDropRate: Double = -15   // km/h/s
        var hardBrakingMinDuration: TimeInterval = 0.4
        var sharpTurnHeadingRate: Double = 25         // °/s
        var sharpTurnLatAccel: Double = 3.0
        var sharpTurnMinDuration: TimeInterval = 0.8
        var rapidAccelMin: Double = 3.5              // m/s²
        var rapidAccelSpeedIncRate: Double = 15      // km/h/s
        var rapidAccelMinDuration: TimeInterval = 0.4
    }

    var config = Config()

    // MARK: - 状态
    private var laneChangeState = LaneChangeState()
    private var brakingState = SpeedChangeState()
    private var accelState = SpeedChangeState()
    private var turnState = TurnState()

    private var lastEventTime: Date = .distantPast

    // MARK: - 核心入口
    func process(point: DataPoint) -> [DrivingEvent] {
        var events: [DrivingEvent] = []

        if let e = detectLaneChange(point) { events.append(e) }
        if let e = detectHardBraking(point) { events.append(e) }
        if let e = detectRapidAccel(point) { events.append(e) }
        if let e = detectSharpTurn(point) { events.append(e) }

        return events
    }

    // MARK: - 变道检测（三信号融合）
    struct LaneChangeState {
        var detecting = false
        var startTime: Date = .distantPast
        var direction: EventType?
        var startHeading: Double = 0
        var peakLatAccel: Double = 0
        var confirmedUntil: Date = .distantPast
    }

    private func detectLaneChange(_ point: DataPoint) -> DrivingEvent? {
        let now = point.timestamp

        // 防重复：确认后 3 秒内不触发
        if now < laneChangeState.confirmedUntil { return nil }
        // 速度检查
        if point.speedKmh < config.laneChangeMinSpeed { return nil }

        let absLat = abs(point.lateralAccel)

        if !laneChangeState.detecting {
            // 阶段1：检测起始
            guard absLat >= config.laneChangeLatAccelMin else { return nil }
            laneChangeState.detecting = true
            laneChangeState.startTime = now
            laneChangeState.direction = point.lateralAccel > 0 ? .laneChangeRight : .laneChangeLeft
            laneChangeState.startHeading = point.heading
            laneChangeState.peakLatAccel = absLat
            return nil
        }

        // 阶段2：等待确认
        let elapsed = now.timeIntervalSince(laneChangeState.startTime)
        if absLat > laneChangeState.peakLatAccel { laneChangeState.peakLatAccel = absLat }

        // 超时 → 放弃
        if elapsed > config.laneChangeMaxDuration {
            resetLaneChange(now); return nil
        }

        // 航向变化检查
        let hdgChange = abs(MathUtils.headingDelta(current: point.heading, previous: laneChangeState.startHeading))
        if hdgChange > config.laneChangeHeadingMax {
            resetLaneChange(now); return nil // 可能在转弯
        }

        // 横向加速度回落 → 变道完成
        let fading = absLat < 0.5
        let minMet = elapsed >= config.laneChangeMinDuration
        let hdgOk = hdgChange >= config.laneChangeHeadingMin

        if fading && minMet && hdgOk {
            let severity = min(1.0, max(0.1, (laneChangeState.peakLatAccel / 4.0) * 0.6 + min(elapsed / 2.0, 1.0) * 0.4))
            let desc = "\(laneChangeState.direction!.displayName) (横向: \(String(format: "%.1f", laneChangeState.peakLatAccel)) m/s², 航向变化: \(String(format: "%.1f", hdgChange))°)"
            let event = DrivingEvent(
                tripId: point.tripId, timestamp: now,
                eventType: laneChangeState.direction!,
                latitude: point.latitude, longitude: point.longitude,
                speedKmh: point.speedKmh, heading: point.heading,
                severity: severity, description: desc
            )
            resetLaneChange(now)
            return event
        }

        return nil
    }

    private func resetLaneChange(_ now: Date) {
        laneChangeState = LaneChangeState()
        laneChangeState.confirmedUntil = now.addingTimeInterval(3)
    }

    // MARK: - 急刹车检测
    struct SpeedChangeState {
        var detecting = false
        var startTime: Date = .distantPast
        var startSpeed: Double = 0
        var peakValue: Double = 0  // 峰值减速度或加速度
        var debounceUntil: Date = .distantPast
    }

    private func detectHardBraking(_ point: DataPoint) -> DrivingEvent? {
        let now = point.timestamp
        if now < brakingState.debounceUntil { return nil }

        if !brakingState.detecting {
            guard point.longitudinalAccel <= config.hardBrakingDecelMin else { return nil }
            brakingState.detecting = true
            brakingState.startTime = now
            brakingState.startSpeed = point.speedKmh
            brakingState.peakValue = abs(point.longitudinalAccel)
            return nil
        }

        let elapsed = now.timeIntervalSince(brakingState.startTime)
        let absDecel = abs(point.longitudinalAccel)
        if absDecel > brakingState.peakValue { brakingState.peakValue = absDecel }

        let decelEnded = point.longitudinalAccel > -1.0
        let minMet = elapsed >= config.hardBrakingMinDuration
        let speedDrop = brakingState.startSpeed - point.speedKmh

        if decelEnded && minMet && speedDrop >= 5 {
            let severity = min(1.0, max(0.1, (brakingState.peakValue / 8.0) * 0.5 + (speedDrop / 30.0) * 0.3 + (1 - elapsed / 2.0) * 0.2))
            let event = DrivingEvent(
                tripId: point.tripId, timestamp: now, eventType: .hardBraking,
                latitude: point.latitude, longitude: point.longitude,
                speedKmh: point.speedKmh, heading: point.heading, severity: severity,
                description: "急刹车: 减速度 \(String(format: "%.1f", brakingState.peakValue)) m/s², 降速 \(String(format: "%.0f", speedDrop)) km/h"
            )
            brakingState = SpeedChangeState()
            brakingState.debounceUntil = now.addingTimeInterval(3)
            return event
        }

        if elapsed > 2.0 { brakingState = SpeedChangeState() }
        return nil
    }

    // MARK: - 急加速检测
    private func detectRapidAccel(_ point: DataPoint) -> DrivingEvent? {
        let now = point.timestamp
        if now < accelState.debounceUntil { return nil }

        if !accelState.detecting {
            guard point.speedKmh > 5 && point.longitudinalAccel >= config.rapidAccelMin else { return nil }
            accelState.detecting = true
            accelState.startTime = now; accelState.startSpeed = point.speedKmh
            accelState.peakValue = point.longitudinalAccel
            return nil
        }

        let elapsed = now.timeIntervalSince(accelState.startTime)
        if point.longitudinalAccel > accelState.peakValue { accelState.peakValue = point.longitudinalAccel }
        let speedGain = point.speedKmh - accelState.startSpeed

        let accelEnded = point.longitudinalAccel < 1.0
        let minMet = elapsed >= config.rapidAccelMinDuration

        if accelEnded && minMet && speedGain >= 5 {
            let severity = min(1.0, max(0.1, (accelState.peakValue / 6.0) * 0.5 + (speedGain / 25.0) * 0.3 + (1 - elapsed / 2.0) * 0.2))
            let event = DrivingEvent(
                tripId: point.tripId, timestamp: now, eventType: .rapidAcceleration,
                latitude: point.latitude, longitude: point.longitude,
                speedKmh: point.speedKmh, heading: point.heading, severity: severity,
                description: "急加速: \(String(format: "%.1f", accelState.peakValue)) m/s², 提速 \(String(format: "%.0f", speedGain)) km/h"
            )
            accelState = SpeedChangeState()
            accelState.debounceUntil = now.addingTimeInterval(3)
            return event
        }

        if elapsed > 2.0 { accelState = SpeedChangeState() }
        return nil
    }

    // MARK: - 急转弯检测
    struct TurnState {
        var detecting = false; var startTime: Date = .distantPast
        var prevHeading: Double = -1; var prevTime: Date = .distantPast
        var cumulativeHdgChange: Double = 0
        var peakHeadingRate: Double = 0; var peakLatAccel: Double = 0
        var debounceUntil: Date = .distantPast
    }

    private func detectSharpTurn(_ point: DataPoint) -> DrivingEvent? {
        let now = point.timestamp
        if now < turnState.debounceUntil { return nil }

        if turnState.prevHeading < 0 {
            turnState.prevHeading = point.heading; turnState.prevTime = now
            return nil
        }

        let dt = now.timeIntervalSince(turnState.prevTime)
        guard dt > 0 else { return nil }

        let hdgDelta = abs(MathUtils.headingDelta(current: point.heading, previous: turnState.prevHeading))
        let hdgRate = hdgDelta / dt
        turnState.prevHeading = point.heading; turnState.prevTime = now
        let absLat = abs(point.lateralAccel)

        if !turnState.detecting {
            guard hdgRate >= config.sharpTurnHeadingRate && absLat >= config.sharpTurnLatAccel else { return nil }
            turnState.detecting = true; turnState.startTime = now
            turnState.cumulativeHdgChange = 0; turnState.peakHeadingRate = hdgRate; turnState.peakLatAccel = absLat
            return nil
        }

        let elapsed = now.timeIntervalSince(turnState.startTime)
        turnState.cumulativeHdgChange += hdgDelta
        if hdgRate > turnState.peakHeadingRate { turnState.peakHeadingRate = hdgRate }
        if absLat > turnState.peakLatAccel { turnState.peakLatAccel = absLat }

        let ended = hdgRate < config.sharpTurnHeadingRate * 0.4
        let minMet = elapsed >= config.sharpTurnMinDuration

        if ended && minMet && abs(turnState.cumulativeHdgChange) >= 15 {
            let direction: EventType = turnState.cumulativeHdgChange > 0 ? .sharpTurnRight : .sharpTurnLeft
            let severity = min(1.0, max(0.1, (turnState.peakHeadingRate / 50.0) * 0.4 + (turnState.peakLatAccel / 5.0) * 0.3 + (abs(turnState.cumulativeHdgChange) / 90.0) * 0.3))
            let event = DrivingEvent(
                tripId: point.tripId, timestamp: now, eventType: direction,
                latitude: point.latitude, longitude: point.longitude,
                speedKmh: point.speedKmh, heading: point.heading, severity: severity,
                description: "急转弯: 航向变化率 \(String(format: "%.1f", turnState.peakHeadingRate))°/s, 转角 \(String(format: "%.0f", abs(turnState.cumulativeHdgChange)))°"
            )
            turnState = TurnState()
            turnState.debounceUntil = now.addingTimeInterval(2)
            return event
        }

        if elapsed > 5 { turnState = TurnState() }
        return nil
    }

    func reset() {
        laneChangeState = LaneChangeState()
        brakingState = SpeedChangeState()
        accelState = SpeedChangeState()
        turnState = TurnState()
        lastEventTime = .distantPast
    }
}
