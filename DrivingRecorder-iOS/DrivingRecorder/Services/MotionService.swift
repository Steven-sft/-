import Foundation
import CoreMotion

/// 运动传感器服务（加速度计 50Hz）
@MainActor
final class MotionService: ObservableObject {
    private let motionManager = CMMotionManager()

    @Published var lateralAccel: Double = 0     // 横向加速度 m/s²
    @Published var longitudinalAccel: Double = 0 // 纵向加速度 m/s²
    @Published var verticalAccel: Double = 0     // 垂直加速度 m/s²

    // 滤波状态
    private var filteredX: Double = 0
    private var filteredY: Double = 0
    private var filteredZ: Double = 0

    var isAvailable: Bool { motionManager.isAccelerometerAvailable }

    func startUpdating() {
        guard motionManager.isAccelerometerAvailable else { return }

        motionManager.accelerometerUpdateInterval = 1.0 / 50.0  // 50Hz

        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1

        motionManager.startAccelerometerUpdates(to: queue) { [weak self] data, error in
            guard let self = self, let data = data else { return }

            let rawX = data.acceleration.x * 9.81  // G → m/s²
            let rawY = data.acceleration.y * 9.81
            let rawZ = data.acceleration.z * 9.81

            // 一阶低通滤波
            let alpha = 0.85
            self.filteredX = MathUtils.lowPassFilter(current: rawX, previous: self.filteredX, alpha: alpha)
            self.filteredY = MathUtils.lowPassFilter(current: rawY, previous: self.filteredY, alpha: alpha)
            self.filteredZ = MathUtils.lowPassFilter(current: rawZ, previous: self.filteredZ, alpha: alpha)

            // 转换到车辆坐标系（iPhone 顶部朝车前方）
            Task { @MainActor in
                self.lateralAccel = self.filteredX     // 横向
                self.longitudinalAccel = self.filteredY // 纵向
                self.verticalAccel = self.filteredZ     // 垂直
            }
        }
    }

    func stopUpdating() {
        motionManager.stopAccelerometerUpdates()
        filteredX = 0; filteredY = 0; filteredZ = 0
    }
}
