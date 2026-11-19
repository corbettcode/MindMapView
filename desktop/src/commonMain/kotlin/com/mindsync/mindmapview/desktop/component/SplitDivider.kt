package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The test tag of the line between the outline and the map. */
const val SPLIT_DIVIDER_TAG = "splitDivider"

/**
 * The line between two panes side by side, which is dragged to share the width between them. Each move of a drag
 * calls [onDrag] with how far it went, in pixels, and its end [onDragEnd].
 */
@Composable
fun SplitDivider(
    onDrag: (dx: Float) -> Unit
    , onDragEnd: () -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    // Wider than the line it draws, so it is easy to take hold of.
    Box(
        contentAlignment = Alignment.Center
        , modifier = Modifier
            .width(7.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { currentOnDragEnd() }
                    , onDragCancel = { currentOnDragEnd() }
                ) { change, dx ->
                    change.consume()
                    currentOnDrag(dx)
                }
            }
            .testTag(SPLIT_DIVIDER_TAG)
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
