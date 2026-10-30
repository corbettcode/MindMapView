package com.mindsync.mindmapview.desktop.menuBar

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun ToolMenu(
    onImport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Tool")
        }
        DropdownMenu(
            expanded = expanded
            , onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Import...") }
                , onClick = {
                    expanded = false
                    onImport()
                }
            )
        }
    }
}
