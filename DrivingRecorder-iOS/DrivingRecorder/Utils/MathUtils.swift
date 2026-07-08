import Foundation

/// 数学和传感器计算工具
enum MathUtils {
    static let earthRadiusM = 6_371_000.0

    /// 一阶低通滤波
    static func lowPassFilter(current: Double, previous: Double, alpha: Double = 0.85) -> Double {
        return alpha * current + (1 - alpha) * previous
    }

    /// Haversine 距离（米）
    static func haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let dLat = (lat2 - lat1).degreesToRadians
        let dLon = (lon2 - lon1).degreesToRadians
        let a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1.degreesToRadians) * cos(lat2.degreesToRadians) *
                sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    /// 航向角变化量（处理 0/360 度环绕）
    static func headingDelta(current: Double, previous: Double) -> Double {
        var delta = current - previous
        if delta > 180 { delta -= 360 }
        if delta < -180 { delta += 360 }
        return delta
    }

    /// 米/秒 → 公里/小时
    static func msToKmh(_ ms: Double) -> Double { ms * 3.6 }

    /// 公里/小时 → 米/秒
    static func kmhToMs(_ kmh: Double) -> Double { kmh / 3.6 }

    /// 移动窗口标准差
    static func standardDeviation(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        let mean = values.reduce(0, +) / Double(values.count)
        let variance = values.reduce(0) { $0 + pow($1 - mean, 2) } / Double(values.count)
        return sqrt(variance)
    }

    /// 检测过零点
    static func hasZeroCrossing(prev: Double, current: Double) -> Bool {
        return (prev > 0 && current <= 0) || (prev < 0 && current >= 0)
    }
}

extension Double {
    var degreesToRadians: Double { self * .pi / 180 }
    var radiansToDegrees: Double { self * 180 / .pi }
}
