package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.rememberMindMapState

private enum class Operation { ADD, ADD_STADIUM, UPDATE }

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
                    Button(onClick = { operation = Operation.ADD }, enabled = selectedNode != null) {
                        Text("Add")
                    }
                    Button(onClick = { operation = Operation.ADD_STADIUM }, enabled = selectedNode != null) {
                        Text("Add stadium")
                    }
                    Button(onClick = { operation = Operation.UPDATE }, enabled = selectedNode != null) {
                        Text("Edit")
                    }
                    Button(onClick = { state.removeNode() }, enabled = selectedNode != null && selectedNode !is CircleNodeData) {
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
                description = if (currentOperation == Operation.UPDATE) selectedNode?.description.orEmpty() else "",
                onSubmit = { description ->
                    when (currentOperation) {
                        Operation.ADD -> state.addNode(description)
                        Operation.ADD_STADIUM -> state.addNode(description, NodeShape.STADIUM)
                        Operation.UPDATE -> state.editNodeText(description)
                    }
                },
                onDismiss = { operation = null },
            )
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
