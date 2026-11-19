package com.mindsync.mindmapview.desktop.menuBar

import androidx.compose.foundation.layout.Box
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
import com.corbettcode.mindmapview.library.model.MapStructure
import com.mindsync.mindmapview.desktop.FOLD_COMMANDS
import com.mindsync.mindmapview.desktop.FoldCommand
import com.mindsync.mindmapview.desktop.ViewMode

@Composable
fun ViewMenu(
    onFit: () -> Unit,
    isCenterEnabled: Boolean,
    onCenter: () -> Unit,
    onFold: (FoldCommand) -> Unit,
    onFilter: () -> Unit,
    viewMode: ViewMode,
    onViewMode: (ViewMode) -> Unit,
    structure: MapStructure,
    onStructure: (MapStructure) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("View")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    leadingIcon = { Text(if (mode == viewMode) "✓" else "") },
                    // The key goes round the modes, so it is shown by the one it goes to.
                    trailingIcon = {
                        if (mode == viewMode.next) {
                            Text("Ctrl+Shift+O", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    onClick = {
                        expanded = false
                        onViewMode(mode)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Fit") },
                onClick = {
                    expanded = false
                    onFit()
                },
            )
            DropdownMenuItem(
                text = { Text("Center Selected Node") },
                trailingIcon = { Text("Ctrl+Alt+C", style = MaterialTheme.typography.labelMedium) },
                enabled = isCenterEnabled,
                onClick = {
                    expanded = false
                    onCenter()
                },
            )
            DropdownMenuItem(
                text = { Text("Filter...") },
                trailingIcon = { Text("Ctrl+Shift+F", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onFilter()
                },
            )
            StructureSubmenu(structure = structure, onStructure = onStructure, onClose = { expanded = false })
            HorizontalDivider()
            FOLD_COMMANDS.forEach { command ->
                DropdownMenuItem(
                    text = { Text(command.label) },
                    trailingIcon = { Text(command.shortcut, style = MaterialTheme.typography.labelMedium) },
                    onClick = {
                        expanded = false
                        onFold(command)
                    },
                )
            }
        }
    }
}
