package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.README_MARKDOWN
import com.mindsync.mindmapview.desktop.UrlImageLoader
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

private val README_DIALOG_SIZE = DpSize(800.dp, 700.dp)

// The image loader shows the README's images, and the rich text library has it as experimental.
@OptIn(ExperimentalRichTextApi::class)
@Composable
fun ReadmeDialog(
    onDismiss: () -> Unit
) {
    val readmeState = remember { RichTextState().setMarkdown(README_MARKDOWN) }

    AppDialog(
        title = "README"
        , state = remember { AppDialogState(README_DIALOG_SIZE) }
        , onCloseRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            RichText(
                state = readmeState
                , imageLoader = UrlImageLoader
                , modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
            Row(
                horizontalArrangement = Arrangement.End
                , modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
