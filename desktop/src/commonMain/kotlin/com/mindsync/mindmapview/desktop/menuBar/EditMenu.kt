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

/**
 * Composable function for rendering an "Edit" dropdown menu in the UI.
 *
 * @param canUndo Determines whether the "Undo" action is enabled.
 * @param onUndo Callback function invoked when the "Undo" action is selected.
 * @param canRedo Determines whether the "Redo" action is enabled.
 * @param onRedo Callback function invoked when the "Redo" action is selected.
 * @param enabled Determines whether the general actions in the menu are enabled.
 * @param onAdd Callback function invoked when an item is added. Receives the `NodeShape` of the item to be added.
 * @param onEdit Callback function invoked when the "Edit" action is selected.
 * @param removeEnabled Determines whether the "Remove" action is enabled.
 * @param onRemove Callback function invoked when the "Remove" action is selected.
 * @param onCut Callback function invoked when the "Cut" action is selected.
 * @param onCopy Callback function invoked when the "Copy" action is selected.
 * @param pasteEnabled Function that determines whether the "Paste" action is enabled.
 * @param onPaste Callback function invoked when the "Paste" action is selected.
 * @param onSelectAll Callback function invoked when the "Select All" action is selected.
 * @param canMove Function that determines whether the "Move" actions are enabled.
 * @param onMoveAround Callback function invoked when a "Move" action is selected. Receives a boolean indicating whether the move is clockwise.
 * @param links Function that provides a list of `RichTextLink` objects representing the links available to open.
 * @param onOpenLink Callback function invoked when an "Open Link" action is selected. Receives the URL of the link.
 * @param onAddArrowLink Callback function invoked when the "Add Arrow Link" action is selected.
 * @param onEditAttributes Callback function invoked when the "Attributes..." action is selected.
 * @param onPreferences Callback function invoked when the "Preferences..." action is selected.
 * @param onFind Callback function invoked when the "Find..." action is selected.
 */
@Composable
fun EditMenu(
    canUndo: Boolean
    , onUndo: () -> Unit
    , canRedo: Boolean
    , onRedo: () -> Unit
    , enabled: Boolean
    , onAdd: (NodeShape) -> Unit
    , onEdit: () -> Unit
    , removeEnabled: Boolean
    , onRemove: () -> Unit
    , onCut: () -> Unit
    , onCopy: () -> Unit
    , pasteEnabled: () -> Boolean
    , onPaste: () -> Unit
    , onSelectAll: () -> Unit
    , canMove: () -> Boolean
    , onMoveAround: (isClockwise: Boolean) -> Unit
    , links: () -> List<RichTextLink>
    , onOpenLink: (String) -> Unit
    , onAddArrowLink: () -> Unit
    , onEditAttributes: () -> Unit
    , onPreferences: () -> Unit
    , onFind: () -> Unit
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
            }
        ) {
            DropdownMenuItem(
                text = { Text("Undo") }
                , trailingIcon = { Text("Ctrl+Z", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onUndo()
                }
                , enabled = canUndo
            )
            DropdownMenuItem(
                text = { Text("Redo") }
                , trailingIcon = { Text("Ctrl+Y", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onRedo()
                }
                , enabled = canRedo
            )

            HorizontalDivider()

            Box {
                DropdownMenuItem(
                    text = { Text("Add") }
                    , trailingIcon = { Text("\u25B8") }
                    , onClick = { addExpanded = true }
                    , enabled = enabled
                )
                DropdownMenu(
                    expanded = addExpanded
                    , onDismissRequest = { addExpanded = false }
                ) {
                    NodeShape.entries.forEach { shape ->
                        DropdownMenuItem(
                            text = { Text(shape.label) }
                            , onClick = {
                                addExpanded = false
                                expanded = false
                                onAdd(shape)
                            }
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text("Edit") }
                , onClick = {
                    expanded = false
                    onEdit()
                }
                , enabled = enabled
            )
            DropdownMenuItem(
                text = { Text("Attributes...") }
                , trailingIcon = { Text("Alt+F9", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onEditAttributes()
                }
                , enabled = enabled
            )
            DropdownMenuItem(
                text = { Text("Remove") }
                , onClick = {
                    expanded = false
                    onRemove()
                }
                , enabled = removeEnabled
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cut") }
                , trailingIcon = { Text("Ctrl+X", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onCut()
                }
                , enabled = removeEnabled
            )
            DropdownMenuItem(
                text = { Text("Copy") }
                , trailingIcon = { Text("Ctrl+C", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onCopy()
                }
                , enabled = enabled
            )
            DropdownMenuItem(
                text = { Text("Paste") }
                , trailingIcon = { Text("Ctrl+V", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onPaste()
                }
                , enabled = pasteEnabled()
            )
            DropdownMenuItem(
                text = { Text("Select All") }
                , trailingIcon = { Text("Ctrl+A", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onSelectAll()
                }
            )
            DropdownMenuItem(
                text = { Text("Find...") }
                , trailingIcon = { Text("Ctrl+F", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onFind()
                }
            )

            HorizontalDivider()

            val isMoveEnabled = canMove()
            listOf(
                Triple("Move Clockwise \u21BB", "Ctrl+M", true)
                , Triple("Move Counterclockwise \u21BA", "Ctrl+Shift+M", false)
            )
            .forEach { (text, shortcut, isClockwise) ->
                DropdownMenuItem(
                    text = { Text(text) }
                    , trailingIcon = { Text(shortcut, style = MaterialTheme.typography.labelMedium) }
                    , onClick = {
                        expanded = false
                        onMoveAround(isClockwise)
                    }
                    , enabled = isMoveEnabled
                )
            }
            DropdownMenuItem(
                text = { Text("Add Arrow Link") }
                , trailingIcon = { Text("Ctrl+L", style = MaterialTheme.typography.labelMedium) }
                , onClick = {
                    expanded = false
                    onAddArrowLink()
                }
                , enabled = enabled
            )
            val nodeLinks = links()
            Box {
                DropdownMenuItem(
                    text = { Text("Open Link") }
                    , trailingIcon = { Text("\u25B8") }
                    , onClick = { linksExpanded = true }
                    , enabled = nodeLinks.isNotEmpty()
                )
                DropdownMenu(expanded = linksExpanded, onDismissRequest = { linksExpanded = false }) {
                    nodeLinks.forEach { link ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (link.text == link.url) link.url else "${link.text} \u2014 ${link.url}"
                                    , maxLines = 1
                                    , overflow = TextOverflow.Ellipsis
                                    , modifier = Modifier.widthIn(max = LINK_MENU_MAX_WIDTH)
                                )
                            }
                            , onClick = {
                                linksExpanded = false
                                expanded = false
                                onOpenLink(link.url)
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Preferences...") }
                , onClick = {
                    expanded = false
                    onPreferences()
                }
            )
        }
    }
}