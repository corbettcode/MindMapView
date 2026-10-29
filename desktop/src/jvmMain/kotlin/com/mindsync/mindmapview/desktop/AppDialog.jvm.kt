package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import java.awt.Dimension

// A window of its own, so it can be moved away from the mind map and resized.
@Composable
actual fun AppDialog(
    title: String
    , state: AppDialogState
    , onCloseRequest: () -> Unit
    , isResizable: Boolean
    , content: @Composable () -> Unit
) {
    val windowState = rememberDialogState(size = state.size)
    DialogWindow(
        onCloseRequest = onCloseRequest
        , state = windowState
        , title = title
        , resizable = isResizable
    ) {
        // The size goes both ways: the dialog may change it, and so may the user, by resizing the window.
        // The minimum size is set before the size, as a window is not made smaller than its minimum size, which a
        // dialog lowers along with its size when it shrinks.
        fun applyMinSize() {
            window.minimumSize = state.minSize?.let { Dimension(it.width.value.toInt(), it.height.value.toInt()) }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { state.size }.collect {
                applyMinSize()
                windowState.size = it
            }
        }
        LaunchedEffect(Unit) { snapshotFlow { windowState.size }.collect { state.size = it } }
        LaunchedEffect(Unit) { snapshotFlow { state.minSize }.collect { applyMinSize() } }
        content()
    }
}
