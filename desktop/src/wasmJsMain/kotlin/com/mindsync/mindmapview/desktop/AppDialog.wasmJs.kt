package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Over the page, as a page has no windows of its own. It is as large as the dialog asks for, short of the page,
// and a click beside it does not close it, which would lose what was typed in it.
// On a page smaller than the dialog, as on a phone, its content is laid out at the size it can be shrunk to,
// short of which it would not fit, and scrolls instead.
@Composable
actual fun AppDialog(
    title: String
    , state: AppDialogState
    , onCloseRequest: () -> Unit
    , isResizable: Boolean
    , content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onCloseRequest
        , properties = DialogProperties(
            dismissOnClickOutside = false
            , usePlatformDefaultWidth = false
        )
    ) {
        // The dialog is laid out as large as it asks for, so it is made no larger than the page here.
        val page = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.toSize().toDpSize() }
        Surface(
            shape = RoundedCornerShape(12.dp)
            , shadowElevation = 8.dp
            , modifier = Modifier.size(
                width = min(state.size.width, page.width - PAGE_MARGIN * 2)
                , height = min(state.size.height, page.height - PAGE_MARGIN * 2)
            )
        ) {
            Column {
                Text(
                    title
                    , style = MaterialTheme.typography.titleMedium
                    , modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 24.dp
                            , top = 16.dp
                            , end = 24.dp
                        )
                )
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val minSize = state.minSize ?: state.size
                    val width = max(maxWidth, minSize.width)
                    val height = max(maxHeight, minSize.height - TITLE_HEIGHT)
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
    }
}

// What is left of the page around a dialog that would fill it, to show it is over the page.
private val PAGE_MARGIN = 8.dp

// What the title takes of the dialog's height, which [AppDialogState.minSize] counts as a window's title bar does.
private val TITLE_HEIGHT = 40.dp
