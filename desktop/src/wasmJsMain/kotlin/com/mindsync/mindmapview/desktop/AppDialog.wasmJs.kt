package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Over the page, as a page has no windows of its own. It is as large as the dialog asks for, short of the page,
// and a click beside it does not close it, which would lose what was typed in it.
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
        Surface(
            shape = RoundedCornerShape(12.dp)
            , shadowElevation = 8.dp
            , modifier = Modifier.size(state.size)
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
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}
