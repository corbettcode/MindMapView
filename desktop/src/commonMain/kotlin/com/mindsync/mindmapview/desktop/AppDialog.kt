package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.max

/** The size of a dialog, which it may change, and the size it is kept at the least where it can be resized. */
class AppDialogState(
    size: DpSize
    , minSize: DpSize? = null
) {
    var size by mutableStateOf(size)
    var minSize by mutableStateOf(minSize)

    /**
     * The height the dialog's content needs at the least, for a dialog that lays out and scrolls its content itself.
     * The dialog is fitted to it, its title bar included, and kept no shorter.
     */
    var contentMinHeight by mutableStateOf<Dp?>(null)

    private var fittedContentMinHeight: Dp? = null

    /** Fits the dialog to [contentMinHeight] and a title bar of [titleBarHeight]. */
    internal fun fitToContent(titleBarHeight: Dp) {
        val content = contentMinHeight ?: return
        val minHeight = content + titleBarHeight
        size = DpSize(size.width, resizedHeight(size.height, fittedContentMinHeight?.plus(titleBarHeight), minHeight))
        minSize = DpSize(minSize?.width ?: size.width, minHeight)
        fittedContentMinHeight = content
    }
}

/**
 * The height of a dialog at [current] once its least height goes from [oldMin] to [newMin]: that least height to
 * start with, and after that one as much taller or shorter, so room the user gave it is kept, but no shorter.
 */
internal fun resizedHeight(
    current: Dp
    , oldMin: Dp?
    , newMin: Dp
): Dp = if (oldMin == null) newMin else max(newMin, current + (newMin - oldMin))

/** A dialog with a [title]: a window of its own on the desktop, and one over the page in a browser. */
@Composable
expect fun AppDialog(
    title: String
    , state: AppDialogState
    , onCloseRequest: () -> Unit
    , isResizable: Boolean = true
    , content: @Composable () -> Unit
)
