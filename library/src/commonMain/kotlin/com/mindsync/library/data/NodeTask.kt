package com.mindsync.library.data

/**
 * What a node holds of a task: its [priority], from 1, the highest, to 9, how far it has [progress]ed, and the dates
 * it [start]s and is [due] on. Each is null when the node has none. A node without any of them is no task.
 *
 * @throws IllegalArgumentException when [priority] is not one of 1 to 9.
 */
data class NodeTask(
    val priority: Int? = null
    , val progress: TaskProgress? = null
    , val start: TaskDate? = null
    , val due: TaskDate? = null
) {
    init {
        require(priority == null || priority in PRIORITIES) { "A priority is one of 1 to 9, not $priority" }
    }

    /** Whether the node holds nothing of a task. */
    val isEmpty: Boolean get() = priority == null && progress == null && start == null && due == null

    companion object {
        /** The priorities a task can have, the highest first. */
        val PRIORITIES = 1..9
    }
}

/** How far a task has gone, in the quarters Freeplane marks it in. */
enum class TaskProgress(val percent: Int) {
    NOT_STARTED(0)
    , QUARTER(25)
    , HALF(50)
    , THREE_QUARTERS(75)
    , DONE(100)
    ;

    /** The progress a step on from this, and from done back to not started. */
    val next: TaskProgress get() = entries[(ordinal + 1) % entries.size]
}

/**
 * A day of the calendar, without a time or a time zone, as a task starts or is due on one. It is written as
 * `YYYY-MM-DD`, as in ISO 8601.
 *
 * @throws IllegalArgumentException when there is no such day, as February 30.
 */
data class TaskDate(
    val year: Int
    , val month: Int
    , val day: Int
) : Comparable<TaskDate> {
    init {
        require(year in YEARS && month in 1..12 && day in 1..daysIn(year, month)) {
            "There is no day $year-$month-$day"
        }
    }

    override fun compareTo(other: TaskDate): Int = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    /** Returns the date as it is written, `YYYY-MM-DD`. */
    fun toIsoString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    override fun toString(): String = toIsoString()

    companion object {
        // Four digits are what a date is written with.
        private val YEARS = 1..9999
        private val ISO_DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")

        /** Returns the date written as `YYYY-MM-DD` in [text], or null when it is no such date. */
        fun parse(text: String): TaskDate? {
            val (year, month, day) = ISO_DATE.matchEntire(text.trim())?.destructured ?: return null
            return try {
                TaskDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun daysIn(
            year: Int
            , month: Int
        ): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
}
