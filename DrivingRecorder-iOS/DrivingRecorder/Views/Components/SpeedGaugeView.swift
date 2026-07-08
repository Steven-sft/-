import SwiftUI

/// 速度表盘组件
struct SpeedGaugeView: View {
    let speedKmh: Double
    let maxSpeed: Double

    var body: some View {
        ZStack {
            // 背景圆弧
            Circle()
                .trim(from: 0.125, to: 0.875) // 135° to 405° = 270°
                .stroke(Color.gray.opacity(0.3), style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(135))

            // 速度指示圆弧
            Circle()
                .trim(from: 0.125, to: 0.125 + 0.75 * min(speedKmh / maxSpeed, 1.0))
                .stroke(gaugeColor, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(135))
                .animation(.easeInOut(duration: 0.3), value: speedKmh)

            // 中心数字
            VStack(spacing: 0) {
                Text("\(Int(speedKmh))")
                    .font(.system(size: 48, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
                Text("km/h")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .frame(width: 200, height: 200)
    }

    private var gaugeColor: Color {
        let ratio = speedKmh / maxSpeed
        if ratio < 0.5 { return .green }
        if ratio < 0.75 { return .orange }
        return .red
    }
}
