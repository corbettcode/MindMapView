package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** What to do with the unsaved changes of a mind map before another one is shown or the app is closed. */
enum class SaveChoice { SAVE, DISCARD, CANCEL }

@Composable
fun SaveChangesDialog(
    question: String,
    onChoice: (SaveChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onChoice(SaveChoice.CANCEL) },
        title = { Text(question) },
        text = { Text("Your changes are lost if you don't save them.") },
        confirmButton = {
            Button(onClick = { onChoice(SaveChoice.SAVE) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoice(SaveChoice.DISCARD) }) {
                    Text("Don't Save")
                }
                TextButton(onClick = { onChoice(SaveChoice.CANCEL) }) {
                    Text("Cancel")
                }
            }
        },
    )
}
