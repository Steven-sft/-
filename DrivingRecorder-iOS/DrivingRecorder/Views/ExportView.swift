import SwiftUI

/// 导出视图
struct ExportView: View {
    let trip: Trip?
    let points: [DataPoint]
    let events: [DrivingEvent]

    @State private var isExporting = false
    @State private var showShare = false
    @State private var sharedURL: URL?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // 行程摘要
                if let trip = trip {
                    GroupBox("行程摘要") {
                        DataRow(label: "日期", value: Formatters.dateFormatter.string(from: trip.startTime))
                        DataRow(label: "时长", value: Formatters.formatDurationChinese(trip.durationSec))
                        DataRow(label: "距离", value: Formatters.formatDistance(trip.totalDistanceM))
                        DataRow(label: "数据点", value: "\(points.count) 条 (5Hz)")
                        DataRow(label: "事件", value: "\(events.count) 个")
                    }
                }

                // 导出按钮
                Button(action: { exportCSV() }) {
                    Label("导出 CSV（Excel 兼容）", systemImage: "tablecells")
                        .frame(maxWidth: .infinity).frame(height: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
                .disabled(trip == nil || isExporting)

                Button(action: { exportJSON() }) {
                    Label("导出 JSON（结构化数据）", systemImage: "curlybraces")
                        .frame(maxWidth: .infinity).frame(height: 48)
                }
                .buttonStyle(.borderedProminent)
                .tint(.blue)
                .disabled(trip == nil || isExporting)

                if isExporting {
                    ProgressView("导出中...")
                }
            }
            .padding()
        }
        .sheet(isPresented: $showShare) {
            if let url = sharedURL {
                ShareSheet(items: [url])
            }
        }
    }

    private func exportCSV() {
        guard let trip = trip else { return }
        isExporting = true
        DispatchQueue.global().async {
            let url = ExportService.exportCSV(trip: trip, points: points, events: events)
            DispatchQueue.main.async {
                isExporting = false
                sharedURL = url
                showShare = true
            }
        }
    }

    private func exportJSON() {
        guard let trip = trip else { return }
        isExporting = true
        DispatchQueue.global().async {
            let url = ExportService.exportJSON(trip: trip, points: points, events: events)
            DispatchQueue.main.async {
                isExporting = false
                sharedURL = url
                showShare = true
            }
        }
    }
}

/// UIKit Share Sheet bridge
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
