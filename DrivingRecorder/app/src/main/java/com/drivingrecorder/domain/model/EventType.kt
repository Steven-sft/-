package com.drivingrecorder.domain.model

/**
 * 驾驶事件类型枚举
 */
enum class EventType(val displayName: String, val category: EventCategory) {
    TRIP_START("行程开始", EventCategory.SYSTEM),
    TRIP_END("行程结束", EventCategory.SYSTEM),

    LANE_CHANGE_LEFT("向左变道", EventCategory.LANE_CHANGE),
    LANE_CHANGE_RIGHT("向右变道", EventCategory.LANE_CHANGE),

    HARD_BRAKING("急刹车", EventCategory.SPEED_CHANGE),
    RAPID_ACCELERATION("急加速", EventCategory.SPEED_CHANGE),

    SHARP_TURN_LEFT("左急转弯", EventCategory.TURN),
    SHARP_TURN_RIGHT("右急转弯", EventCategory.TURN);

    companion object {
        fun fromName(name: String): EventType =
            entries.firstOrNull { it.name == name } ?: TRIP_START
    }
}

enum class EventCategory {
    SYSTEM,
    LANE_CHANGE,
    SPEED_CHANGE,
    TURN
}
