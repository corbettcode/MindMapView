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

// A date picker holds the start of a day in UTC, in milliseconds since 1970-01-01. The conversions are the civil
// calendar ones of Howard Hinnant's date algorithms, so the app needs no date library for them.

/** Returns the start of [date] in UTC, in milliseconds since 1970-01-01, as a date picker holds it. */
internal fun TaskDate.toUtcMillis(): Long {
    val y = if (month <= 2) year - 1 else year
    val era = y.floorDiv(400)
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return (era * 146_097L + dayOfEra - 719_468L) * MILLIS_PER_DAY
}

/** Returns the day in UTC that [millis] since 1970-01-01 fall on, as a date picker gives it. */
internal fun taskDateOfUtcMillis(millis: Long): TaskDate {
    val days = millis.floorDiv(MILLIS_PER_DAY) + 719_468L
    val era = days.floorDiv(146_097L)
    val dayOfEra = days - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * monthIndex + 2) / 5 + 1).toInt()
    val month = (if (monthIndex < 10) monthIndex + 3 else monthIndex - 9).toInt()
    val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
    return TaskDate(year, month, day)
}
