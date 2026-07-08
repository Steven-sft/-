import Foundation

/// 驾驶事件类型
enum EventType: String, Codable, CaseIterable {
    case tripStart = "TRIP_START"
    case tripEnd = "TRIP_END"
    case laneChangeLeft = "LANE_CHANGE_LEFT"
    case laneChangeRight = "LANE_CHANGE_RIGHT"
    case hardBraking = "HARD_BRAKING"
    case rapidAcceleration = "RAPID_ACCELERATION"
    case sharpTurnLeft = "SHARP_TURN_LEFT"
    case sharpTurnRight = "SHARP_TURN_RIGHT"

    var displayName: String {
        switch self {
        case .tripStart: return "行程开始"
        case .tripEnd: return "行程结束"
        case .laneChangeLeft: return "向左变道"
        case .laneChangeRight: return "向右变道"
        case .hardBraking: return "急刹车"
        case .rapidAcceleration: return "急加速"
        case .sharpTurnLeft: return "左急转弯"
        case .sharpTurnRight: return "右急转弯"
        }
    }

    var category: EventCategory {
        switch self {
        case .tripStart, .tripEnd: return .system
        case .laneChangeLeft, .laneChangeRight: return .laneChange
        case .hardBraking, .rapidAcceleration: return .speedChange
        case .sharpTurnLeft, .sharpTurnRight: return .turn
        }
    }
}

enum EventCategory: String {
    case system, laneChange, speedChange, turn
}
