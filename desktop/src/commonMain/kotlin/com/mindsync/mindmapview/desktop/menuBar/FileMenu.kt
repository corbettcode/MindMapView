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
import com.mindsync.mindmapview.desktop.fileNameOf
import com.mindsync.mindmapview.desktop.sharedNames

class FileActions(
    val new: () -> Unit,
    val open: () -> Unit,
    val openRecent: (path: String) -> Unit,
    val import: () -> Unit,
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
    // Null where mind maps cannot be opened again from where they were, as in a browser.
    recentPaths: List<String>?,
    recentFolderOf: (path: String) -> String?,
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
            if (recentPaths != null) Box {
                DropdownMenuItem(
                    text = { Text("Recent") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { recentExpanded = true },
                    enabled = recentPaths.isNotEmpty(),
                )
                DropdownMenu(expanded = recentExpanded, onDismissRequest = { recentExpanded = false }) {
                    val sharedNames = sharedNames(recentPaths)
                    recentPaths.forEach { path ->
                        val name = fileNameOf(path)
                        DropdownMenuItem(
                            // The folder tells apart mind maps of the same name.
                            text = {
                                Column {
                                    Text(name)
                                    val folder = recentFolderOf(path)
                                    if (name in sharedNames && folder != null) {
                                        Text(folder, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                            onClick = {
                                recentExpanded = false
                                onExpandedChange(false)
                                actions.openRecent(path)
                            },
                        )
                    }
                }
            }
            FileMenuItem("Import...", null, actions.import, onExpandedChange)
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
    shortcut: String?,
    onClick: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = shortcut?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        onClick = {
            onExpandedChange(false)
            onClick()
        },
    )
}
