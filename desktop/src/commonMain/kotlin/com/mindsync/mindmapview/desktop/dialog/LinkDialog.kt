package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.mindsync.mindmapview.desktop.LINK_DIALOG_FIELD_WIDTH
import com.mindsync.mindmapview.desktop.LINK_SCHEMES
import com.mindsync.mindmapview.desktop.completeUrl
import com.mindsync.mindmapview.desktop.linkScheme

@Composable
fun LinkDialog(
    url: String?,
    onSubmit: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(url.orEmpty()) }
    val completed = completeUrl(input)
    val isOpenable = linkScheme(completed) in LINK_SCHEMES
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (url == null) "Add link" else "Edit link") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Address") },
                placeholder = { Text("https://") },
                singleLine = true,
                isError = input.isNotBlank() && !isOpenable,
                supportingText = {
                    if (input.isNotBlank() && !isOpenable) Text("Only web and mail addresses can be linked to.")
                },
                modifier = Modifier.width(LINK_DIALOG_FIELD_WIDTH).focusRequester(focusRequester),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(completed)
                    onDismiss()
                },
                enabled = isOpenable,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (url != null) {
                    TextButton(
                        onClick = {
                            onRemove()
                            onDismiss()
                        },
                    ) {
                        Text("Remove link")
                    }
                }
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
