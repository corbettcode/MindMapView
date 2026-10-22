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
import com.mindsync.mindmapview.desktop.FOLD_COMMANDS
import com.mindsync.mindmapview.desktop.FoldCommand

@Composable
fun ViewMenu(
    onFit: () -> Unit,
    showAttributes: Boolean,
    onShowAttributesChange: (Boolean) -> Unit,
    onFold: (FoldCommand) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("View")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Fit") },
                onClick = {
                    expanded = false
                    onFit()
                },
            )
            HorizontalDivider()
            // The one of the two that is in effect is checked.
            DropdownMenuItem(
                text = { Text("Show All Attributes") },
                leadingIcon = { if (showAttributes) Text("\u2713") },
                trailingIcon = { Text("Alt+S", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onShowAttributesChange(true)
                },
            )
            DropdownMenuItem(
                text = { Text("Show No Attributes") },
                leadingIcon = { if (!showAttributes) Text("\u2713") },
                trailingIcon = { Text("Alt+H", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onShowAttributesChange(false)
                },
            )
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
