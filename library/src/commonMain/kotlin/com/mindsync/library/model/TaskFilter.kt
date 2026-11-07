package com.mindsync.library.model

import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskDate
import com.mindsync.library.data.TaskProgress

/**
 * Which nodes a filter of tasks keeps, by what they hold of a task. A node matches when it meets every condition the
 * filter sets; a condition that is null, or false, is not set, so `TaskFilter()` matches every node.
 *
 * @property priorities The priorities a node can have to match. A node without a priority does not match them.
 * @property progress Whether a node is to be done or not done to match, or either when both are in it.
 * @property isOverdueOnly Whether only a node due before today and not done matches.
 * @property dueWithinDays How many days on from today a node not done can be due on to match, today being 0. One
 * that is overdue already does not match it.
 */
data class TaskFilter(
    val priorities: Set<Int>? = null
    , val progress: Set<ProgressFilter>? = null
    , val isOverdueOnly: Boolean = false
    , val dueWithinDays: Int? = null
) {
    /** Whether the filter sets no condition, and so matches every node. */
    val isEmpty: Boolean get() = priorities == null && progress == null && !isOverdueOnly && dueWithinDays == null

    /**
     * Returns whether a node that holds [task] matches the filter, with [today] the date it is. Without [today], no
     * node is overdue or due within days, as the library reads no clock.
     */
    fun matches(
        task: NodeTask
        , today: TaskDate?
    ): Boolean {
        val isDone = task.progress == TaskProgress.DONE
        val isPriorityMatched = priorities?.let { task.priority in it } ?: true
        val isProgressMatched = progress?.any { it.matches(task) } ?: true
        val isOverdueMatched = !isOverdueOnly || (today != null && task.due != null && task.due < today && !isDone)
        // Days are compared rather than added, so a filter of many days near the year 9999 can't go past it.
        val isDueMatched = dueWithinDays?.let { days ->
            today != null && task.due != null && !isDone && task.due.toEpochDay() - today.toEpochDay() in 0..days
        } ?: true
        return isPriorityMatched && isProgressMatched && isOverdueMatched && isDueMatched
    }
}

/** How far a task has gone, as a filter of tasks asks it. */
enum class ProgressFilter {
    /** A task, a node with anything of one, that is not done. */
    NOT_DONE

    /** A task that is done. */
    , DONE
    ;

    internal fun matches(task: NodeTask): Boolean = when (this) {
        NOT_DONE -> !task.isEmpty && task.progress != TaskProgress.DONE
        DONE -> task.progress == TaskProgress.DONE
    }
}

/**
 * How a filter of tasks shows the map: the nodes drawn faded, and the folded nodes with a match hidden below them,
 * which have a dot in their fold ring.
 */
internal class TaskFilterView(
    val fadedIds: Set<String>
    , val hiddenMatchIds: Set<String>
) {
    companion object {
        /** No filter: nothing faded and no dots, as the map is exported. */
        val NONE = TaskFilterView(emptySet(), emptySet())
    }
}
