import SwiftUI

/// 仪表盘视图
struct DashboardView: View {
    @ObservedObject var recording: RecordingService

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // 速度表盘
                SpeedGaugeView(
                    speedKmh: recording.isRecording ? recording.currentSpeed : 0,
                    maxSpeed: 160
                )
                .padding(.top, 20)

                // 录制状态卡片
                if recording.isRecording {
                    HStack {
                        VStack {
                            Text(Formatters.formatDuration(recording.elapsedSeconds))
                                .font(.title2).fontWeight(.bold)
                            Text("时长").font(.caption).foregroundColor(.secondary)
                        }.frame(maxWidth: .infinity)
                        Divider()
                        VStack {
                            Text(String(format: "%.2f", recording.totalDistance / 1000))
                                .font(.title2).fontWeight(.bold)
                            Text("距离(km)").font(.caption).foregroundColor(.secondary)
                        }.frame(maxWidth: .infinity)
                    }
                    .padding()
                    .background(.red.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                // 实时数据
                GroupBox("GPS 数据") {
                    DataRow(label: "纬度", value: String(format: "%.6f", recording.currentLatitude))
                    DataRow(label: "经度", value: String(format: "%.6f", recording.currentLongitude))
                    DataRow(label: "航向角", value: "\(String(format: "%.0f", recording.currentHeading))°")
                    DataRow(label: "海拔", value: "\(String(format: "%.1f", recording.currentAltitude))m")
                }

                GroupBox("传感器数据") {
                    DataRow(label: "横向加速度", value: "\(String(format: "%.3f", recording.lateralAccel)) m/s²")
                    DataRow(label: "纵向加速度", value: "\(String(format: "%.3f", recording.longitudinalAccel)) m/s²")
                }

                // 开始/停止按钮
                Button(action: {
                    Task {
                        if recording.isRecording {
                            _ = await recording.stopRecording()
                        } else {
                            _ = await recording.startRecording()
                        }
                    }
                }) {
                    HStack {
                        Image(systemName: recording.isRecording ? "stop.fill" : "play.fill")
                        Text(recording.isRecording ? "停止记录" : "开始记录")
                    }
                    .font(.title3).fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(recording.isRecording ? Color.red : Color.blue)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .padding(.top, 8)
            }
            .padding()
        }
    }
}

struct DataRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).foregroundColor(.secondary)
            Spacer()
            Text(value).fontWeight(.medium)
        }
        .font(.subheadline)
        .padding(.vertical, 2)
    }
}
