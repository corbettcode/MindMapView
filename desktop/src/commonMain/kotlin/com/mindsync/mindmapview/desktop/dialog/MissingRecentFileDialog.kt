package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Tells that the recent file at [path] is not found and asks whether to remove it from the recent files. */
@Composable
fun MissingRecentFileDialog(
    path: String
    , onChoice: (isRemoved: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onChoice(false) }
        , title = { Text("File not found") }
        , text = { Text(missingRecentFileMessage(path)) }
        , confirmButton = {
            Button(onClick = { onChoice(true) }) {
                Text("Remove")
            }
        }
        , dismissButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text("Keep")
            }
        }
    )
}

fun missingRecentFileMessage(
    path: String
): String = "The mind map could not be found:\n$path\n\nRemove it from the recent files?"
