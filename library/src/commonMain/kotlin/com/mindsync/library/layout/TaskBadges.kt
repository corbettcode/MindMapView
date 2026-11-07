package com.mindsync.library.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.util.toPx

/** How wide and high a badge of a task is. */
internal val TASK_BADGE_SIZE = 14.dp

/** How far apart the badges of a task are. */
internal val TASK_BADGE_GAP = 4.dp

/** How far the last badge of a task is from the text of its node. */
internal val TASK_BADGE_TEXT_GAP = 6.dp

/** A mark drawn in front of a node's text for what it holds of a task, in the order they are drawn. */
internal sealed interface TaskBadge {
    data class Priority(val priority: Int) : TaskBadge

    data class Progress(val progress: TaskProgress) : TaskBadge
}

/** Returns the badges of [task]: its priority, then its progress, of those it has. */
internal fun taskBadges(task: NodeTask): List<TaskBadge> = listOfNotNull(
    task.priority?.let { TaskBadge.Priority(it) }
    , task.progress?.let { TaskBadge.Progress(it) }
)

/** Returns how wide [count] badges are, in pixels, with the gap to the text after them, or 0 for none. */
internal fun Density.taskBadgesWidth(count: Int): Float = if (count == 0) {
    0f
}
else {
    TASK_BADGE_SIZE.toPx() * count + TASK_BADGE_GAP.toPx() * (count - 1) + TASK_BADGE_TEXT_GAP.toPx()
}

/** Returns the center of each of the badges of [node], whose content is [content], in the pixels of the tree. */
internal fun Density.taskBadgeCenters(
    node: NodeData<*>
    , content: NodeContent
    , count: Int
): List<Offset> {
    val size = TASK_BADGE_SIZE.toPx()
    val step = size + TASK_BADGE_GAP.toPx()
    val left = node.path.centerX.toPx(this) + content.badgesLeft
    val centerY = node.path.centerY.toPx(this) - content.textRise
    return (0 until count).map { Offset(left + step * it + size / 2, centerY) }
}

/**
 * The colors of the priorities, from 1 to 9: red, orange, yellow, green and blue for the first five, as XMind has
 * them, then gray. Each is dark or light enough for its number in [priorityNumberColor] to be read on it.
 */
internal val PRIORITY_COLORS = listOf(
    Color(0xFFE53935)
    , Color(0xFFF57C00)
    , Color(0xFFFBC02D)
    , Color(0xFF43A047)
    , Color(0xFF1E88E5)
    , Color(0xFF757575)
    , Color(0xFF757575)
    , Color(0xFF757575)
    , Color(0xFF757575)
)

internal fun priorityColor(priority: Int): Color = PRIORITY_COLORS[priority - 1]

// Yellow is too light for white on it.
internal fun priorityNumberColor(priority: Int): Color = if (priority == 3) Color.Black else Color.White
