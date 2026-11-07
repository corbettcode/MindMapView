package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.TaskDate
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskDatesTest {
    @Test
    fun dueDateIsWrittenWithItsYearOnlyInAnotherYear() {
        val today = TaskDate(2026, 11, 7)

        assertEquals("Due 30 Nov", dueLabel(TaskDate(2026, 11, 30), today))
        assertEquals("Due 1 Jan 2027", dueLabel(TaskDate(2027, 1, 1), today))
        assertEquals("Due 1 Jan 2027", dueLabel(TaskDate(2027, 1, 1), null))
    }

    @Test
    fun datesAreTheDaysADatePickerHolds() {
        val days = listOf(
            TaskDate(1970, 1, 1), TaskDate(1969, 12, 31), TaskDate(2000, 2, 29), TaskDate(2026, 11, 7)
            , TaskDate(2100, 3, 1), TaskDate(1, 1, 1), TaskDate(9999, 12, 31)
        )

        days.forEach { date ->
            val millis = LocalDate.of(date.year, date.month, date.day).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli()
            assertEquals(millis, date.toUtcMillis(), "$date")
            assertEquals(date, taskDateOfUtcMillis(millis), "$date")
            assertEquals(date, taskDateOfUtcMillis(millis + 86_399_999L), "the end of $date")
        }
    }
}
