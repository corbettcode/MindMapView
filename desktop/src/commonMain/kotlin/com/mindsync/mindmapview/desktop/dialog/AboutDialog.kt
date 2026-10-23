package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mindsync.mindmapview.desktop.APP_VERSION

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss
        , title = { Text("About MindMapView") }
        , text = {
            Column {
                Text("Developed by corbettcode using Claude Code")
                Text("Version $APP_VERSION")
            }
        }
        , confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
