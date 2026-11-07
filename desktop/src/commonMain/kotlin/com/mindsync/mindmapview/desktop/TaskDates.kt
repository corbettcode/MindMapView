package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.TaskDate

/** Returns the date it is where the app runs, in the local time zone. */
internal expect fun today(): TaskDate

private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Returns [date] as "30 Nov", with its year when it is in another year than [today], or [today] is not known, as
 * "30 Nov 2027".
 */
internal fun shortDate(
    date: TaskDate
    , today: TaskDate?
): String {
    val dayAndMonth = "${date.day} ${MONTH_NAMES[date.month - 1]}"
    return if (date.year == today?.year) dayAndMonth else "$dayAndMonth ${date.year}"
}

/** Returns how the date a node is due on is written below its text, as "Due 30 Nov". */
internal fun dueLabel(
    date: TaskDate
    , today: TaskDate?
): String = "Due ${shortDate(date, today)}"

/** Returns the start of [date] in UTC, in milliseconds since 1970-01-01, as a date picker holds it. */
internal fun TaskDate.toUtcMillis(): Long = toEpochDay() * MILLIS_PER_DAY

/** Returns the day in UTC that [millis] since 1970-01-01 fall on, as a date picker gives it. */
internal fun taskDateOfUtcMillis(millis: Long): TaskDate = TaskDate.ofEpochDay(millis.floorDiv(MILLIS_PER_DAY))
