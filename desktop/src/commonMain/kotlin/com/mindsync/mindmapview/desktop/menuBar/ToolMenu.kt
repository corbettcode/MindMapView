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
import com.mindsync.mindmapview.desktop.ImportFormat

@Composable
internal fun ToolMenu(
    onImport: (ImportFormat) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var importExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Tool")
        }
        DropdownMenu(
            expanded = expanded
            , onDismissRequest = {
                expanded = false
                importExpanded = false
            }
        ) {
            Box {
                DropdownMenuItem(
                    text = { Text("Import") }
                    , trailingIcon = { Text("▸") }
                    , onClick = { importExpanded = true }
                )
                DropdownMenu(
                    expanded = importExpanded
                    , onDismissRequest = { importExpanded = false }
                ) {
                    listOf(
                        "Map..." to ImportFormat.MAP
                        , "Markdown (md)..." to ImportFormat.MARKDOWN
                    ).forEach { (text, format) ->
                        DropdownMenuItem(
                            text = { Text(text) }
                            , onClick = {
                                importExpanded = false
                                expanded = false
                                onImport(format)
                            }
                        )
                    }
                }
            }
        }
    }
}
