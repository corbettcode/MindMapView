package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import java.awt.Dimension
import java.awt.GraphicsEnvironment

// A window of its own, so it can be moved away from the mind map and resized.
// It is as large as the dialog asks for, short of the screen. On a screen smaller than the dialog, its content is
// laid out at the size it can be shrunk to, short of which it would not fit, and scrolls instead, as in a browser.
@Composable
actual fun AppDialog(
    title: String
    , state: AppDialogState
    , onCloseRequest: () -> Unit
    , isResizable: Boolean
    , content: @Composable () -> Unit
) {
    val screen = remember { usableScreenSize() }
    val windowState = rememberDialogState(size = state.size.fittedTo(screen))
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
            window.minimumSize = state.minSize
                ?.fittedTo(screen)
                ?.let { Dimension(it.width.value.toInt(), it.height.value.toInt()) }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { state.size }.collect {
                applyMinSize()
                windowState.size = it.fittedTo(screen)
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { windowState.size }.collect {
                if (isResizedByUser(it, asked = state.size, screen = screen)) state.size = it
            }
        }
        LaunchedEffect(Unit) { snapshotFlow { state.minSize }.collect { applyMinSize() } }
        // The window's title bar is part of the size a dialog asks for, but not of the room its content has.
        val titleBarHeight = window.insets.top.dp
        LaunchedEffect(Unit) { snapshotFlow { state.contentMinHeight }.collect { state.fitToContent(titleBarHeight) } }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val minSize = state.minSize ?: state.size
            val width = max(maxWidth, minSize.width)
            // A dialog that scrolls its content itself is given the room there is.
            val height =
                if (state.contentMinHeight != null) maxHeight else max(maxHeight, minSize.height - titleBarHeight)
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier.size(
                        width = width
                        , height = height
                    )
                ) {
                    content()
                }
            }
        }
    }
}

/** This size, made no larger than [screen]. */
internal fun DpSize.fittedTo(
    screen: DpSize
): DpSize = DpSize(min(width, screen.width), min(height, screen.height))

/**
 * Whether the window being [windowSize] is the user's doing. The window is kept on the screen, so a window of the
 * size [asked] for, short of the screen, is not.
 */
internal fun isResizedByUser(
    windowSize: DpSize
    , asked: DpSize
    , screen: DpSize
): Boolean = windowSize != asked.fittedTo(screen)

// The room on the main screen, short of task bars and docks.
private fun usableScreenSize(): DpSize = GraphicsEnvironment
    .getLocalGraphicsEnvironment()
    .maximumWindowBounds
    .let { DpSize(it.width.dp, it.height.dp) }
