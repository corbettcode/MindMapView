package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeAttribute
import com.mindsync.mindmapview.desktop.emojiVisualTransformation

/** The rows to edit a node's [attributes] in: a node without attributes starts with an empty row to fill in. */
fun attributeRowsOf(attributes: List<NodeAttribute>): List<NodeAttribute> =
    attributes.ifEmpty { listOf(NodeAttribute("", "")) }

/** The attributes the [rows] are, with their names trimmed: a row that was given no name is no attribute. */
fun attributesToKeep(rows: List<NodeAttribute>): List<NodeAttribute> =
    rows.map { it.copy(name = it.name.trim()) }.filter { it.name.isNotEmpty() }

/**
 * A node's attributes as rows of a name and a value, which can be changed, removed and added to. A row can be added
 * of each of the [knownNames] other nodes have attributes of. Enter goes on to the next field: from a name to its
 * value, and from a value to the name on the next row, which is added after the last row. With [isFocusedOnStart],
 * the first name is focused, to type one in at once.
 */
@Composable
fun AttributesEditor(
    rows: SnapshotStateList<NodeAttribute>
    , knownNames: List<String>
    , isFocusedOnStart: Boolean
    , modifier: Modifier = Modifier
) {
    val unusedNames = knownNames.filter { name -> rows.none { it.name == name } }
    // The fields by row and column, the name being column 0 and the value column 1.
    val focusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    fun focusRequester(
        row: Int
        , column: Int
    ): FocusRequester = focusRequesters.getOrPut(row to column) { FocusRequester() }
    // The field to focus once it is there, as a row that was just added is not yet.
    var pendingFocus by remember { mutableStateOf(if (isFocusedOnStart) 0 to 0 else null) }
    LaunchedEffect(pendingFocus) {
        pendingFocus?.let { (row, column) -> focusRequester(row, column).requestFocus() }
        pendingFocus = null
    }

    fun onEnter(
        row: Int
        , column: Int
    ) {
        when {
            column == 0 -> pendingFocus = row to 1
            row < rows.lastIndex -> pendingFocus = row + 1 to 0
            else -> {
                rows.add(NodeAttribute("", ""))
                pendingFocus = rows.lastIndex to 0
            }
        }
    }

    fun Modifier.goesOnWithEnter(
        row: Int
        , column: Int
    ): Modifier = focusRequester(focusRequester(row, column)).onPreviewKeyEvent { event ->
        when {
            event.key != Key.Enter && event.key != Key.NumPadEnter -> false
            event.type == KeyEventType.KeyDown -> {
                onEnter(row, column)
                true
            }
            else -> true
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
        , modifier = modifier.fillMaxWidth()
    ) {
        rows.forEachIndexed { index, attribute ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
                , verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = attribute.name
                    , visualTransformation = emojiVisualTransformation()
                    , onValueChange = { rows[index] = attribute.copy(name = it) }
                    , label = { Text("Name") }
                    , singleLine = true
                    , modifier = Modifier.weight(1f).goesOnWithEnter(index, 0)
                )
                OutlinedTextField(
                    value = attribute.value
                    , visualTransformation = emojiVisualTransformation()
                    , onValueChange = { rows[index] = attribute.copy(value = it) }
                    , label = { Text("Value") }
                    , singleLine = true
                    , modifier = Modifier.weight(1.4f).goesOnWithEnter(index, 1)
                )
                TextButton(onClick = { rows.removeAt(index) }) {
                    Text("✕")
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
            , verticalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = { rows.add(NodeAttribute("", "")) }) {
                Text("+ Add attribute")
            }
            // The names other nodes have attributes of, each of which adds a row of that name.
            unusedNames.forEach { name ->
                TextButton(onClick = { rows.add(NodeAttribute(name, "")) }) {
                    Text("+ $name")
                }
            }
        }
    }
}
