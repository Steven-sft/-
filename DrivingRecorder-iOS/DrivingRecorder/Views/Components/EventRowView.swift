import SwiftUI

/// 事件行组件
struct EventRowView: View {
    let event: DrivingEvent

    var body: some View {
        HStack(spacing: 12) {
            // 彩色圆点
            Circle()
                .fill(eventColor)
                .frame(width: 12, height: 12)

            VStack(alignment: .leading, spacing: 4) {
                Text(event.eventType.displayName)
                    .font(.subheadline)
                    .fontWeight(.bold)

                HStack {
                    Text("\(String(format: "%.0f", event.speedKmh)) km/h")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("严重: \(String(format: "%.0f", event.severity * 100))%")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(Formatters.timeFormatter.string(from: event.timestamp))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                Text(event.description)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 4)
    }

    private var eventColor: Color {
        switch event.eventType {
        case .laneChangeLeft, .laneChangeRight: return .orange
        case .hardBraking: return .red
        case .rapidAcceleration: return Color(red: 1, green: 0.43, blue: 0)
        case .sharpTurnLeft, .sharpTurnRight: return .purple
        default: return .blue
        }
    }
}
