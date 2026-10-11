package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mindsync.library.MindMap
import com.mindsync.library.data.NodeShape
import com.mindsync.library.rememberMindMapState

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
                MindMap(state = state, modifier = Modifier.fillMaxSize())
            }
        }

        operation?.let { currentOperation ->
            EditDescriptionDialog(
                description = if (currentOperation is Operation.Update) selectedNode?.description.orEmpty() else "",
                onSubmit = { description ->
                    when (currentOperation) {
                        is Operation.Add -> state.addNode(description, currentOperation.shape)
                        Operation.Update -> state.editNodeText(description)
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

@Composable
private fun EditDescriptionDialog(
    description: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(text)
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
