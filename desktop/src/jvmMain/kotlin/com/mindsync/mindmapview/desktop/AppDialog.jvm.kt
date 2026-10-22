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
        LaunchedEffect(Unit) { snapshotFlow { state.size }.collect { windowState.size = it } }
        LaunchedEffect(Unit) { snapshotFlow { windowState.size }.collect { state.size = it } }
        LaunchedEffect(Unit) {
            snapshotFlow { state.minSize }.collect { minSize ->
                window.minimumSize = minSize?.let { Dimension(it.width.value.toInt(), it.height.value.toInt()) }
            }
        }
        content()
    }
}
