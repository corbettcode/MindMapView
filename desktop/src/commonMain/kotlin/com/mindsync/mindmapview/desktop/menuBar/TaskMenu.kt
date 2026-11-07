package com.mindsync.mindmapview.desktop.menuBar

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskProgress

/** How a progress is named in the menus and the edit dialog. */
val TaskProgress.label: String
    get() = when (this) {
        TaskProgress.NOT_STARTED -> "Not Started"
        TaskProgress.QUARTER -> "25%"
        TaskProgress.HALF -> "50%"
        TaskProgress.THREE_QUARTERS -> "75%"
        TaskProgress.DONE -> "Done"
    }

/**
 * A "Task" item of a menu, which opens a menu of the priorities, the progress steps and Toggle Done, each with its
 * key, and a tick at what [task] holds. Each choice closes the menus, with [onClose], before it is made.
 */
@Composable
fun TaskSubmenu(
    task: NodeTask
    , enabled: Boolean
    , onPriority: (Int?) -> Unit
    , onProgress: (TaskProgress?) -> Unit
    , onToggleDone: () -> Unit
    , onClose: () -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    fun choose(action: () -> Unit) {
        isOpen = false
        onClose()
        action()
    }
    Box {
        DropdownMenuItem(
            text = { Text("Task") }
            , trailingIcon = { Text("▸") }
            , onClick = { isOpen = true }
            , enabled = enabled
        )
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            (listOf(null) + NodeTask.PRIORITIES).forEach { priority ->
                DropdownMenuItem(
                    text = { Text(priority?.let { "Priority $it" } ?: "No Priority") }
                    , leadingIcon = { Tick(isShown = task.priority == priority) }
                    , trailingIcon = { Shortcut("Ctrl+${priority ?: 0}") }
                    , onClick = { choose { onPriority(priority) } }
                )
            }
            HorizontalDivider()
            (listOf(null) + TaskProgress.entries).forEach { progress ->
                DropdownMenuItem(
                    text = { Text(progress?.label ?: "No Progress") }
                    , leadingIcon = { Tick(isShown = task.progress == progress) }
                    , onClick = { choose { onProgress(progress) } }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Toggle Done") }
                , trailingIcon = { Shortcut("Ctrl+D") }
                , onClick = { choose(onToggleDone) }
            )
        }
    }
}

@Composable
private fun Tick(isShown: Boolean) {
    Text(if (isShown) "✓" else "")
}

@Composable
private fun Shortcut(keys: String) {
    Text(keys, style = MaterialTheme.typography.labelMedium)
}
