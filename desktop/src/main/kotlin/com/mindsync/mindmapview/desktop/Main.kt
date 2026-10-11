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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mindsync.library.MindMap
import com.mindsync.library.data.NodeShape
import com.mindsync.library.rememberMindMapState
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor

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

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "MindMapView") {
        MindMapScreen()
    }
}

@Composable
private fun MindMapScreen() {
    val state = rememberMindMapState()
    val selectedNode = state.selectedNode
    var operation by remember { mutableStateOf<Operation?>(null) }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddNodeMenu(enabled = selectedNode != null, onAdd = { operation = Operation.Add(it) })
                    Button(onClick = { operation = Operation.Update }, enabled = selectedNode != null) {
                        Text("Edit")
                    }
                    Button(onClick = { state.removeNode() }, enabled = selectedNode?.parentId != null) {
                        Text("Remove")
                    }
                    Button(onClick = { state.fitScreen() }) {
                        Text("Fit")
                    }
                }
                MindMap(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onNodeDoubleClick = { operation = Operation.Update },
                )
            }
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

@Composable
private fun AddNodeMenu(
    enabled: Boolean,
    onAdd: (NodeShape) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(onClick = { expanded = true }, enabled = enabled) {
            Text("Add")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NodeShape.entries.forEach { shape ->
                DropdownMenuItem(
                    text = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        expanded = false
                        onAdd(shape)
                    },
                )
            }
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
