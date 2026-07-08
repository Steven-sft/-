import SwiftUI

/// 主界面：4 个标签页
struct ContentView: View {
    @StateObject private var recording = RecordingService()
    @State private var selectedTab = 0
    @State private var currentTrip: Trip?

    @State private var allPoints: [DataPoint] = []
    @State private var allEvents: [DrivingEvent] = []

    var body: some View {
        TabView(selection: $selectedTab) {
            // 标签1：卫星地图
            MapTabView(recording: recording, points: allPoints, events: allEvents)
                .tabItem { Label("轨迹地图", systemImage: "map") }
                .tag(0)

            // 标签2：仪表盘
            DashboardView(recording: recording)
                .tabItem { Label("仪表盘", systemImage: "gauge.with.dots.needle.33percent") }
                .tag(1)

            // 标签3：事件
            EventsListView(events: recording.isRecording ? recording.recentEvents : allEvents)
                .tabItem {
                    Label("事件(\(recording.isRecording ? recording.recentEvents.count : allEvents.count))",
                          systemImage: "list.bullet.clipboard")
                }
                .tag(2)

            // 标签4：导出
            ExportView(trip: currentTrip, points: allPoints, events: allEvents)
                .tabItem { Label("导出", systemImage: "square.and.arrow.up") }
                .tag(3)
        }
        .onChange(of: recording.isRecording) { _, isRec in
            if !isRec {
                // 录制停止后加载数据用于导出
                Task {
                    if let tripId = recording.currentTripId ?? currentTrip?.id {
                        allPoints = await DataStore.shared.getDataPoints(for: tripId)
                        allEvents = await DataStore.shared.getEvents(for: tripId)
                        currentTrip = await DataStore.shared.getTrip(id: tripId)
                    }
                }
            } else {
                allPoints = []; allEvents = []
            }
        }
        .onChange(of: recording.recentEvents) { _, events in
            if recording.isRecording { allEvents = events }
        }
        .onChange(of: recording.pointCount) { _, _ in
            // 录制中实时更新点数（从 recording 直接读取）
        }
    }
}
