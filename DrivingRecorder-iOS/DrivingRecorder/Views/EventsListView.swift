import SwiftUI

/// 事件列表视图
struct EventsListView: View {
    let events: [DrivingEvent]

    var body: some View {
        if events.isEmpty {
            ContentUnavailableView(
                "暂无驾驶事件",
                systemImage: "car.rear.road.lane",
                description: Text("开始录制后将自动检测变道、急刹车、急转弯等行为")
            )
        } else {
            List(events) { event in
                EventRowView(event: event)
            }
            .listStyle(.plain)
        }
    }
}
