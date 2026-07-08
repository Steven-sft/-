import SwiftUI
import MapKit

/// 卫星地图 + 实时轨迹
struct MapTabView: View {
    @ObservedObject var recording: RecordingService
    let points: [DataPoint]
    let events: [DrivingEvent]

    @State private var camera: MapCameraPosition = .region(MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 39.9139, longitude: 116.4105),
        span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
    ))

    var body: some View {
        ZStack {
            // 卫星地图
            Map(position: $camera) {
                // 轨迹线
                if points.count >= 2 {
                    let coords = points.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
                    MapPolyline(coordinates: coords)
                        .stroke(.cyan, lineWidth: 3)
                }

                // 事件标记
                ForEach(events) { event in
                    Annotation(event.eventType.displayName, coordinate: CLLocationCoordinate2D(
                        latitude: event.latitude, longitude: event.longitude
                    )) {
                        Circle()
                            .fill(eventMarkerColor(event.eventType))
                            .frame(width: 10, height: 10)
                            .overlay(Circle().stroke(.white, lineWidth: 2))
                    }
                }

                // 当前位置
                if recording.isRecording {
                    Annotation("当前位置", coordinate: CLLocationCoordinate2D(
                        latitude: recording.currentLatitude,
                        longitude: recording.currentLongitude
                    )) {
                        ZStack {
                            Circle().fill(.blue.opacity(0.3)).frame(width: 24, height: 24)
                            Circle().fill(.white).frame(width: 12, height: 12)
                            Circle().fill(.blue).frame(width: 8, height: 8)
                        }
                    }
                }
            }
            .mapStyle(.imagery) // 卫星影像
            .mapControls { MapCompass(); MapScaleView() }

            // 速度悬浮面板
            VStack {
                HStack {
                    // 左上：速度
                    VStack(alignment: .leading, spacing: 0) {
                        Text("\(Int(recording.currentSpeed))")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                        Text("km/h")
                            .font(.caption2)
                    }
                    .padding(10)
                    .background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Spacer()

                    // 右上：坐标
                    VStack(alignment: .trailing, spacing: 0) {
                        Text(String(format: "%.6f", recording.currentLatitude))
                        Text(String(format: "%.6f", recording.currentLongitude))
                    }
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.white.opacity(0.8))
                    .padding(8)
                    .background(.black.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }

                Spacer()

                // 录制状态指示
                if recording.isRecording {
                    HStack {
                        Circle().fill(.red).frame(width: 8, height: 8)
                        Text("录制中 \(Formatters.formatDuration(recording.elapsedSeconds))")
                            .font(.caption)
                            .fontWeight(.bold)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())

                    Spacer()

                    // 底部统计条
                    HStack {
                        StatItem(label: "距离", value: Formatters.formatDistance(recording.totalDistance))
                        StatItem(label: "点数", value: "\(recording.pointCount)")
                        StatItem(label: "事件", value: "\(recording.recentEvents.count)")
                        StatItem(label: "5Hz", value: "采集")
                            .foregroundColor(.green)
                    }
                    .font(.caption)
                    .padding(8)
                    .background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .padding()
        }
        .onChange(of: recording.currentLatitude) { _, _ in
            updateCamera()
        }
    }

    private func updateCamera() {
        guard recording.isRecording else { return }
        withAnimation(.easeInOut(duration: 0.5)) {
            camera = .region(MKCoordinateRegion(
                center: CLLocationCoordinate2D(
                    latitude: recording.currentLatitude,
                    longitude: recording.currentLongitude
                ),
                span: MKCoordinateSpan(latitudeDelta: 0.005, longitudeDelta: 0.005)
            ))
        }
    }

    private func eventMarkerColor(_ type: EventType) -> Color {
        switch type {
        case .laneChangeLeft, .laneChangeRight: return .orange
        case .hardBraking: return .red
        case .rapidAcceleration: return Color(red: 1, green: 0.43, blue: 0)
        case .sharpTurnLeft, .sharpTurnRight: return .purple
        default: return .blue
        }
    }
}

struct StatItem: View {
    let label: String
    let value: String

    init(label: String, value: String) { self.label = label; self.value = value }

    var body: some View {
        VStack(spacing: 0) {
            Text(value).fontWeight(.bold)
            Text(label).font(.system(size: 9)).foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
