package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize

/** The size of a dialog, which it may change, and the size it is kept at the least where it can be resized. */
class AppDialogState(
    size: DpSize
    , minSize: DpSize? = null
) {
    var size by mutableStateOf(size)
    var minSize by mutableStateOf(minSize)
}

/** A dialog with a [title]: a window of its own on the desktop, and one over the page in a browser. */
@Composable
expect fun AppDialog(
    title: String
    , state: AppDialogState
    , onCloseRequest: () -> Unit
    , isResizable: Boolean = true
    , content: @Composable () -> Unit
)
