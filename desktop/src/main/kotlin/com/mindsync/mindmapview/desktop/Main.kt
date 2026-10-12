package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mindsync.library.MindMap
import com.mindsync.library.MindMapState
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.rememberMindMapState
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

private const val APP_NAME = "MindMapView"
private const val FREE_MIND_EXTENSION = ".mm"

private val NODE_COLORS = NodeShape.entries.map { it.defaultColor } + listOf(
    Color(0xFF7AA37D),
    Color(0xFF4B877B),
    Color(0xFF336770),
    Color(0xFF8E7CC3),
    Color(0xFF6FA8DC),
)

private val TEXT_COLORS = listOf(
    Color.Black,
    Color.White,
    Color(0xFF5C5C5C),
    Color(0xFFB3261E),
    Color(0xFF0B5394),
    Color(0xFF274E13),
)

private sealed interface Operation {
    data class Add(val shape: NodeShape) : Operation
    data object Update : Operation
}

private class FileActions(
    val new: () -> Unit,
    val open: () -> Unit,
    val save: () -> Unit,
    val saveAs: () -> Unit,
)

fun main() = application {
    val state = rememberMindMapState()
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var fileMenuExpanded by remember { mutableStateOf(false) }

    fun save(target: File) {
        try {
            target.writeText(FreeMind.write(state.tree))
            file = target
        } catch (e: IOException) {
            error = "Could not save ${target.name}: ${e.message}"
        }
    }

    fun saveAs() {
        chooseFile(frame, "Save As", FileDialog.SAVE, file)?.let { chosen ->
            save(if (chosen.extension.isEmpty()) File(chosen.path + FREE_MIND_EXTENSION) else chosen)
        }
    }

    val fileActions = FileActions(
        new = {
            state.setTree(Tree<Any>())
            file = null
        },
        open = {
            chooseFile(frame, "Open", FileDialog.LOAD, file)?.let { chosen ->
                try {
                    state.setTree(FreeMind.read(chosen.readText()))
                    state.animateTreeChange()
                    file = chosen
                } catch (e: IOException) {
                    error = "Could not open ${chosen.name}: ${e.message}"
                } catch (e: IllegalArgumentException) {
                    error = "Could not open ${chosen.name}: ${e.message}"
                }
            }
        },
        save = { file?.let { save(it) } ?: saveAs() },
        saveAs = { saveAs() },
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = file?.let { "${it.name} - $APP_NAME" } ?: APP_NAME,
        onPreviewKeyEvent = { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.isAltPressed && event.key == Key.F -> {
                    fileMenuExpanded = true
                    true
                }
                !event.isCtrlPressed -> false
                event.key == Key.N -> {
                    fileActions.new()
                    true
                }
                event.key == Key.O -> {
                    fileActions.open()
                    true
                }
                event.key == Key.S -> {
                    if (event.isShiftPressed) fileActions.saveAs() else fileActions.save()
                    true
                }
                else -> false
            }
        },
    ) {
        SideEffect { frame = window }
        MindMapScreen(
            state = state,
            error = error,
            onErrorDismiss = { error = null },
            fileActions = fileActions,
            fileMenuExpanded = fileMenuExpanded,
            onFileMenuExpandedChange = { fileMenuExpanded = it },
        )
    }
}

private fun chooseFile(
    window: Frame?,
    title: String,
    mode: Int,
    current: File?,
): File? {
    val dialog = FileDialog(window, title, mode)
    dialog.setFilenameFilter { _, name -> name.endsWith(FREE_MIND_EXTENSION, ignoreCase = true) }
    dialog.directory = current?.parent
    dialog.file = current?.name ?: "*$FREE_MIND_EXTENSION"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

@Composable
private fun MindMapScreen(
    state: MindMapState,
    error: String?,
    onErrorDismiss: () -> Unit,
    fileActions: FileActions,
    fileMenuExpanded: Boolean,
    onFileMenuExpandedChange: (Boolean) -> Unit,
) {
    val selectedNode = state.selectedNode
    var operation by remember { mutableStateOf<Operation?>(null) }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    FileMenu(fileActions, fileMenuExpanded, onFileMenuExpandedChange)
                    EditMenu(
                        enabled = selectedNode != null,
                        onAdd = { operation = Operation.Add(it) },
                        onEdit = { operation = Operation.Update },
                        removeEnabled = selectedNode?.parentId != null,
                        onRemove = { state.removeNode() },
                    )
                    ViewMenu(onFit = { state.fitScreen() })
                }
                MindMap(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onNodeDoubleClick = { operation = Operation.Update },
                )
            }
        }

        error?.let { message ->
            AlertDialog(
                onDismissRequest = onErrorDismiss,
                text = { Text(message) },
                confirmButton = {
                    Button(onClick = onErrorDismiss) {
                        Text("OK")
                    }
                },
            )
        }

        operation?.let { currentOperation ->
            EditNodeDialog(
                description = if (currentOperation is Operation.Update) selectedNode?.description.orEmpty() else "",
                color = when (currentOperation) {
                    is Operation.Add -> currentOperation.shape.defaultColor
                    Operation.Update -> selectedNode?.color ?: Color.Unspecified
                },
                textColor = when (currentOperation) {
                    is Operation.Add -> currentOperation.shape.defaultTextColor
                    Operation.Update -> selectedNode?.textColor ?: Color.Unspecified
                },
                onSubmit = { description, color, textColor ->
                    when (currentOperation) {
                        is Operation.Add -> state.addNode(description, currentOperation.shape, color, textColor)
                        Operation.Update -> {
                            state.editNodeText(description)
                            state.editNodeColor(color)
                            state.editNodeTextColor(textColor)
                        }
                    }
                },
                onDismiss = { operation = null },
            )
        }
    }
}

// Drawn by Compose instead of the window's native menu bar, whose drop down closes as soon as the mouse
// button is released under XWayland.
@Composable
private fun FileMenu(
    actions: FileActions,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box {
        TextButton(onClick = { onExpandedChange(true) }) {
            Text("File")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            FileMenuItem("New", "Ctrl+N", actions.new, onExpandedChange)
            FileMenuItem("Open...", "Ctrl+O", actions.open, onExpandedChange)
            HorizontalDivider()
            FileMenuItem("Save", "Ctrl+S", actions.save, onExpandedChange)
            FileMenuItem("Save As...", "Ctrl+Shift+S", actions.saveAs, onExpandedChange)
        }
    }
}

@Composable
private fun FileMenuItem(
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

@Composable
private fun EditMenu(
    enabled: Boolean,
    onAdd: (NodeShape) -> Unit,
    onEdit: () -> Unit,
    removeEnabled: Boolean,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Edit")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                addExpanded = false
            },
        ) {
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
                            text = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) },
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
                text = { Text("Remove") },
                onClick = {
                    expanded = false
                    onRemove()
                },
                enabled = removeEnabled,
            )
        }
    }
}

@Composable
private fun ViewMenu(onFit: () -> Unit) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNodeDialog(
    description: String,
    color: Color,
    textColor: Color,
    onSubmit: (description: String, color: Color, textColor: Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val richTextState = remember { RichTextState().setHtml(description) }
    var selectedColor by remember { mutableStateOf(color) }
    var selectedTextColor by remember { mutableStateOf(textColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RichTextToolbar(richTextState)
                OutlinedRichTextEditor(
                    state = richTextState,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                ColorPicker(
                    label = "Fill",
                    colors = remember(color) { (NODE_COLORS + color).distinct() },
                    selectedColor = selectedColor,
                    onSelect = { selectedColor = it },
                )
                ColorPicker(
                    label = "Text",
                    colors = remember(textColor) { (TEXT_COLORS + textColor).distinct() },
                    selectedColor = selectedTextColor,
                    onSelect = { selectedTextColor = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(richTextState.toHtml(), selectedColor, selectedTextColor)
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RichTextToolbar(state: RichTextState) {
    val spanStyle = state.currentSpanStyle
    val decoration = spanStyle.textDecoration ?: TextDecoration.None

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RichTextToggle(
            label = "B",
            labelStyle = SpanStyle(fontWeight = FontWeight.Bold),
            checked = spanStyle.fontWeight == FontWeight.Bold,
            onToggle = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        RichTextToggle(
            label = "I",
            labelStyle = SpanStyle(fontStyle = FontStyle.Italic),
            checked = spanStyle.fontStyle == FontStyle.Italic,
            onToggle = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
        )
        RichTextToggle(
            label = "U",
            labelStyle = SpanStyle(textDecoration = TextDecoration.Underline),
            checked = TextDecoration.Underline in decoration,
            onToggle = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
        )
        RichTextToggle(
            label = "S",
            labelStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
            checked = TextDecoration.LineThrough in decoration,
            onToggle = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) },
        )
        RichTextToggle(
            label = "\u2022",
            labelStyle = SpanStyle(),
            checked = state.isUnorderedList,
            onToggle = { state.toggleUnorderedList() },
        )
        RichTextToggle(
            label = "1.",
            labelStyle = SpanStyle(),
            checked = state.isOrderedList,
            onToggle = { state.toggleOrderedList() },
        )
    }
}

// The toggles do not take focus, so the editor keeps its selection while a style is applied.
@Composable
private fun RichTextToggle(
    label: String,
    labelStyle: SpanStyle,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.focusProperties { canFocus = false },
    ) {
        Text(label, style = LocalTextStyle.current.merge(labelStyle))
    }
}

@Composable
private fun ColorPicker(
    label: String,
    colors: List<Color>,
    selectedColor: Color,
    onSelect: (Color) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(40.dp))
        colors.forEach { color ->
            val borderColor =
                if (color == selectedColor) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (color == selectedColor) 3.dp else 1.dp, borderColor, CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}
