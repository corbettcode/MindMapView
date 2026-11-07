package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeTask
import com.mindsync.library.model.ProgressFilter
import com.mindsync.library.model.TaskFilter

/** The days on from today the "Due in 7 days" chip of the filter bar keeps the tasks due within. */
internal const val FILTER_DUE_DAYS = 7

/**
 * A bar of chips that filter the map's nodes by their tasks: priorities 1 to 9, of which any chosen match, not done
 * and done, then overdue or due within [FILTER_DUE_DAYS] days, of which one is chosen at most, as no task is both.
 * The conditions of different kinds all have to be met. [onFilterChange] is given the filter the chips make, or
 * null when none is chosen. Esc closes the bar, which the app handles, as the chips do not hold the focus.
 */
@Composable
internal fun FilterBar(
    filter: TaskFilter?
    , onFilterChange: (TaskFilter?) -> Unit
    , countText: String
    , onClose: () -> Unit
) {
    val current = filter ?: TaskFilter()
    fun change(changed: TaskFilter) = onFilterChange(changed.takeUnless { it.isEmpty })

    // The chips scroll in a narrow window, and the count and buttons after them stay in view.
    Row(
        verticalAlignment = Alignment.CenterVertically
        , modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
            , verticalAlignment = Alignment.CenterVertically
            , modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChips(current, ::change)
        }
        Text(
            countText
            , color = if (countText == NO_MATCHES) MaterialTheme.colorScheme.error else Color.Unspecified
            , style = MaterialTheme.typography.bodyMedium
            , modifier = Modifier.padding(horizontal = 12.dp).testTag(FILTER_COUNT_TAG)
        )
        TextButton(onClick = { onFilterChange(null) }, enabled = filter != null) { Text("Clear") }
        TextButton(onClick = onClose) { Text("✕") }
    }
}

@Composable
private fun FilterChips(
    current: TaskFilter
    , change: (TaskFilter) -> Unit
) {
    Text("Filter", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
    NodeTask.PRIORITIES.forEach { priority ->
        val priorities = current.priorities.orEmpty()
        FilterChip(
            selected = priority in priorities
            , onClick = {
                val changed = if (priority in priorities) priorities - priority else priorities + priority
                change(current.copy(priorities = changed.takeIf { it.isNotEmpty() }))
            }
            , label = { Text("$priority") }
            , modifier = Modifier.testTag("filter priority $priority")
        )
    }
    Divider()
    listOf(ProgressFilter.NOT_DONE to "Not done", ProgressFilter.DONE to "Done").forEach { (progress, label) ->
        val chosen = current.progress.orEmpty()
        FilterChip(
            selected = progress in chosen
            , onClick = {
                val changed = if (progress in chosen) chosen - progress else chosen + progress
                change(current.copy(progress = changed.takeIf { it.isNotEmpty() }))
            }
            , label = { Text(label) }
        )
    }
    Divider()
    FilterChip(
        selected = current.isOverdueOnly
        , onClick = { change(current.copy(isOverdueOnly = !current.isOverdueOnly, dueWithinDays = null)) }
        , label = { Text("Overdue") }
    )
    FilterChip(
        selected = current.dueWithinDays != null
        , onClick = {
            val days = if (current.dueWithinDays == null) FILTER_DUE_DAYS else null
            change(current.copy(dueWithinDays = days, isOverdueOnly = false))
        }
        , label = { Text("Due in $FILTER_DUE_DAYS days") }
    )
}

@Composable
private fun Divider() = VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

private const val NO_MATCHES = "No matches"

/** The test tag of the filter bar's count of matches. */
internal const val FILTER_COUNT_TAG = "filterCount"

/** Returns how many nodes match a filter, as "3 matches", or nothing while there is no filter. */
internal fun filterCountText(
    hasFilter: Boolean
    , matchCount: Int
): String = when {
    !hasFilter -> ""
    matchCount == 0 -> NO_MATCHES
    matchCount == 1 -> "1 match"
    else -> "$matchCount matches"
}
