package com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

class FileActions(
    val new: () -> Unit,
    val open: () -> Unit,
    val openRecent: (File) -> Unit,
    val save: () -> Unit,
    val saveAs: () -> Unit,
    val exportImage: () -> Unit,
    val exportHtml: () -> Unit,
    val exportFreeMind: () -> Unit,
)

// Drawn by Compose instead of the window's native menu bar, whose drop down closes as soon as the mouse
// button is released under XWayland.
@Composable
fun FileMenu(
    actions: FileActions,
    recentPaths: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    var recentExpanded by remember { mutableStateOf(false) }
    var exportExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { onExpandedChange(true) }) {
            Text("File")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                onExpandedChange(false)
                recentExpanded = false
                exportExpanded = false
            },
        ) {
            FileMenuItem("New", "Ctrl+N", actions.new, onExpandedChange)
            FileMenuItem("Open...", "Ctrl+O", actions.open, onExpandedChange)
            Box {
                DropdownMenuItem(
                    text = { Text("Recent") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { recentExpanded = true },
                    enabled = recentPaths.isNotEmpty(),
                )
                DropdownMenu(expanded = recentExpanded, onDismissRequest = { recentExpanded = false }) {
                    recentPaths.forEach { path ->
                        val recentFile = File(path)
                        DropdownMenuItem(
                            // Mind maps in different folders may share a name, so the folder is shown too.
                            text = {
                                Column {
                                    Text(recentFile.name)
                                    Text(recentFile.parent.orEmpty(), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                recentExpanded = false
                                onExpandedChange(false)
                                actions.openRecent(recentFile)
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            FileMenuItem("Save", "Ctrl+S", actions.save, onExpandedChange)
            FileMenuItem("Save As...", "Ctrl+Shift+S", actions.saveAs, onExpandedChange)
            HorizontalDivider()
            Box {
                DropdownMenuItem(
                    text = { Text("Export") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { exportExpanded = true },
                )
                DropdownMenu(expanded = exportExpanded, onDismissRequest = { exportExpanded = false }) {
                    listOf(
                        "As Image (PNG)..." to actions.exportImage,
                        "As HTML Outline..." to actions.exportHtml,
                        "As FreeMind Map (.mm)..." to actions.exportFreeMind,
                    ).forEach { (text, export) ->
                        DropdownMenuItem(
                            text = { Text(text) },
                            onClick = {
                                exportExpanded = false
                                onExpandedChange(false)
                                export()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileMenuItem(
    text: String,
    shortcut: String,
    onClick: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = { Text(shortcut, style = MaterialTheme.typography.labelMedium) },
        onClick = {
            onExpandedChange(false)
            onClick()
        },
    )
}
