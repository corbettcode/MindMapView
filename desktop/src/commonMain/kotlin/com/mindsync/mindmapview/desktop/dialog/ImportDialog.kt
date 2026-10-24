package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Asks whether the nodes of the mind map may be replaced by those of the map to import. */
@Composable
fun ImportDialog(
    onChoice: (isConfirmed: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onChoice(false) }
        , title = { Text("Import mind map") }
        , text = { Text("The nodes of this mind map will be overridden by those of the map you import.") }
        , confirmButton = {
            Button(onClick = { onChoice(true) }) {
                Text("Import")
            }
        }
        , dismissButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text("Cancel")
            }
        }
    )
}
