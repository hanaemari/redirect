package com.example.redirect.ui

import com.example.redirect.data.ReadingStatus
import com.example.redirect.data.RedirectMode
import com.example.redirect.data.RedirectStyle
import com.example.redirect.data.Schedule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun formatMinute(minute: Int): String =
    LocalTime.of((minute / 60) % 24, minute % 60).format(timeFormat)
        .replace(":00", "").lowercase(Locale.getDefault()).replace(" ", "")

fun describeDays(days: Set<Int>): String = when (days) {
    Schedule.ALL_DAYS -> "Every day"
    Schedule.WEEKDAYS -> "Weekdays"
    Schedule.WEEKEND -> "Weekends"
    emptySet<Int>() -> "No days"
    else -> days.sorted().joinToString(", ") { DAY_SHORT[it - 1] }
}

val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val DAY_LETTER = listOf("M", "T", "W", "T", "F", "S", "S")

fun describeSchedule(schedule: Schedule?): String {
    if (schedule == null) return "Any time"
    val days = describeDays(schedule.days)
    return if (schedule.isAllDay) days
    else "$days · ${formatMinute(schedule.startMinute)}–${formatMinute(schedule.endMinute)}"
}

fun ReadingStatus.label() = when (this) {
    ReadingStatus.WANT_TO_READ -> "Want to read"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.FINISHED -> "Finished"
}

fun RedirectMode.label() = when (this) {
    RedirectMode.AUTOMATIC -> "Automatic"
    RedirectMode.CHOICE -> "Choice screen"
}

fun RedirectStyle.label() = when (this) {
    RedirectStyle.AUTOMATIC -> "Automatic redirect"
    RedirectStyle.CHOICE -> "Choice screen"
    RedirectStyle.SCHEDULED -> "Rules and schedules"
}

fun percent(progress: Float) = "${(progress * 100).toInt().coerceIn(0, 100)}%"
