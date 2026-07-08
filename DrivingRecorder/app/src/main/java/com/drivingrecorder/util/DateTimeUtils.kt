package com.drivingrecorder.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 日期时间格式化工具
 */
object DateTimeUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    fun toDateString(epochMs: Long): String = dateFormat.format(Date(epochMs))

    fun toTimeString(epochMs: Long): String = timeFormat.format(Date(epochMs))

    fun toDateTimeString(epochMs: Long): String = dateTimeFormat.format(Date(epochMs))

    fun toIsoString(epochMs: Long): String {
        isoFormat.timeZone = TimeZone.getDefault()
        return isoFormat.format(Date(epochMs))
    }

    /**
     * 格式化时长
     */
    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 格式化时长为中文
     */
    fun formatDurationChinese(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            if (hours > 0) append("${hours}小时")
            if (minutes > 0) append("${minutes}分")
            append("${seconds}秒")
        }
    }
}
