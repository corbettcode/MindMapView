package com.mindsync.mindmapview.desktop.menuBar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mindsync.library.data.NodeShape
import com.mindsync.library.util.RichTextLink
import com.mindsync.mindmapview.desktop.LINK_MENU_MAX_WIDTH
import com.mindsync.mindmapview.desktop.label
import kotlin.collections.forEach

@Composable
fun EditMenu(
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    enabled: Boolean,
    onAdd: (NodeShape) -> Unit,
    onEdit: () -> Unit,
    removeEnabled: Boolean,
    onRemove: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    pasteEnabled: () -> Boolean,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    canMove: () -> Boolean,
    onMoveAround: (isClockwise: Boolean) -> Unit,
    links: () -> List<RichTextLink>,
    onOpenLink: (String) -> Unit,
    onAddArrowLink: () -> Unit,
    onEditAttributes: () -> Unit,
    onPreferences: () -> Unit,
    onFind: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }
    var linksExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Edit")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                addExpanded = false
                linksExpanded = false
            },
        ) {
            DropdownMenuItem(
                text = { Text("Undo") },
                trailingIcon = { Text("Ctrl+Z", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onUndo()
                },
                enabled = canUndo,
            )
            DropdownMenuItem(
                text = { Text("Redo") },
                trailingIcon = { Text("Ctrl+Y", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onRedo()
                },
                enabled = canRedo,
            )
            HorizontalDivider()
            Box {
                DropdownMenuItem(
                    text = { Text("Add") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { addExpanded = true },
                    enabled = enabled,
                )
                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                    NodeShape.entries.forEach { shape ->
                        DropdownMenuItem(
                            text = { Text(shape.label) },
                            onClick = {
                                addExpanded = false
                                expanded = false
                                onAdd(shape)
                            },
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    expanded = false
                    onEdit()
                },
                enabled = enabled,
            )
            DropdownMenuItem(
                text = { Text("Attributes...") },
                trailingIcon = { Text("Alt+F9", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onEditAttributes()
                },
                enabled = enabled,
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    expanded = false
                    onRemove()
                },
                enabled = removeEnabled,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cut") },
                trailingIcon = { Text("Ctrl+X", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onCut()
                },
                enabled = removeEnabled,
            )
            DropdownMenuItem(
                text = { Text("Copy") },
                trailingIcon = { Text("Ctrl+C", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onCopy()
                },
                enabled = enabled,
            )
            DropdownMenuItem(
                text = { Text("Paste") },
                trailingIcon = { Text("Ctrl+V", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onPaste()
                },
                enabled = pasteEnabled(),
            )
            DropdownMenuItem(
                text = { Text("Select All") },
                trailingIcon = { Text("Ctrl+A", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onSelectAll()
                },
            )
            DropdownMenuItem(
                text = { Text("Find...") },
                trailingIcon = { Text("Ctrl+F", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onFind()
                },
            )
            HorizontalDivider()
            val isMoveEnabled = canMove()
            listOf(
                Triple("Move Clockwise \u21BB", "Ctrl+M", true)
                , Triple("Move Counterclockwise \u21BA", "Ctrl+Shift+M", false)
            ).forEach { (text, shortcut, isClockwise) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    trailingIcon = { Text(shortcut, style = MaterialTheme.typography.labelMedium) },
                    onClick = {
                        expanded = false
                        onMoveAround(isClockwise)
                    },
                    enabled = isMoveEnabled,
                )
            }
            DropdownMenuItem(
                text = { Text("Add Arrow Link") },
                trailingIcon = { Text("Ctrl+L", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onAddArrowLink()
                },
                enabled = enabled,
            )
            val nodeLinks = links()
            Box {
                DropdownMenuItem(
                    text = { Text("Open Link") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { linksExpanded = true },
                    enabled = nodeLinks.isNotEmpty(),
                )
                DropdownMenu(expanded = linksExpanded, onDismissRequest = { linksExpanded = false }) {
                    nodeLinks.forEach { link ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (link.text == link.url) link.url else "${link.text} \u2014 ${link.url}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = LINK_MENU_MAX_WIDTH),
                                )
                            },
                            onClick = {
                                linksExpanded = false
                                expanded = false
                                onOpenLink(link.url)
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Preferences...") },
                onClick = {
                    expanded = false
                    onPreferences()
                },
            )
        }
    }
}