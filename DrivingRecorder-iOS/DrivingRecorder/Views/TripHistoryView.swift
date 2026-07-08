import SwiftUI

/// 历史行程列表
struct TripHistoryView: View {
    @State private var trips: [Trip] = []
    @State private var selectedTrip: Trip?
    @State private var tripPoints: [DataPoint] = []
    @State private var tripEvents: [DrivingEvent] = []

    var body: some View {
        NavigationStack {
            if trips.isEmpty {
                ContentUnavailableView(
                    "暂无行程记录",
                    systemImage: "car.rear",
                    description: Text("完成一次驾驶录制后，行程将出现在这里")
                )
            } else {
                List {
                    ForEach(trips) { trip in
                        NavigationLink {
                            TripDetailView(trip: trip)
                        } label: {
                            TripRowView(trip: trip)
                        }
                    }
                    .onDelete { indexSet in
                        for i in indexSet {
                            Task { await DataStore.shared.deleteTrip(id: trips[i].id) }
                        }
                        trips.remove(atOffsets: indexSet)
                    }
                }
                .listStyle(.plain)
                .navigationTitle("行程记录")
                .refreshable { await loadTrips() }
            }
        }
        .task { await loadTrips() }
    }

    private func loadTrips() async {
        trips = await DataStore.shared.getAllTrips()
    }
}

struct TripRowView: View {
    let trip: Trip

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(Formatters.dateFormatter.string(from: trip.startTime))
                    .font(.headline)
                Spacer()
                Text(Formatters.timeFormatter.string(from: trip.startTime))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            HStack {
                Label(Formatters.formatDistance(trip.totalDistanceM), systemImage: "point.topleft.down.curvedto.point.bottomright.up")
                Label(Formatters.formatDuration(trip.durationSec), systemImage: "clock")
                Label("\(trip.eventCount) 事件", systemImage: "exclamationmark.triangle")
            }
            .font(.caption)
            .foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }
}

struct TripDetailView: View {
    let trip: Trip
    @State private var points: [DataPoint] = []
    @State private var events: [DrivingEvent] = []

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // 摘要
                GroupBox("行程概览") {
                    DataRow(label: "日期", value: Formatters.dateFormatter.string(from: trip.startTime))
                    DataRow(label: "时长", value: Formatters.formatDurationChinese(trip.durationSec))
                    DataRow(label: "距离", value: Formatters.formatDistance(trip.totalDistanceM))
                    DataRow(label: "最高速度", value: "\(String(format: "%.0f", trip.maxSpeedKmh)) km/h")
                    DataRow(label: "平均速度", value: "\(String(format: "%.0f", trip.avgSpeedKmh)) km/h")
                    DataRow(label: "数据点", value: "\(trip.pointCount) 条")
                }

                // 事件列表
                if !events.isEmpty {
                    GroupBox("驾驶事件 (\(events.count))") {
                        ForEach(events) { event in
                            EventRowView(event: event)
                            if event.id != events.last?.id { Divider() }
                        }
                    }
                }

                // 导出按钮
                NavigationLink {
                    ExportView(trip: trip, points: points, events: events)
                } label: {
                    Label("导出数据", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity).frame(height: 44)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
        .navigationTitle("行程详情")
        .task {
            points = await DataStore.shared.getDataPoints(for: trip.id)
            events = await DataStore.shared.getEvents(for: trip.id)
        }
    }
}
