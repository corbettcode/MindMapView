package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.mindsync.library.data.NodeShape
import com.mindsync.mindmapview.desktop.DEFAULT_NODE_COLORS
import com.mindsync.mindmapview.desktop.DEFAULT_TEXT_COLORS
import com.mindsync.mindmapview.desktop.ThemeMode
import com.mindsync.mindmapview.desktop.component.ColorPicker
import com.mindsync.mindmapview.desktop.component.HsvColorPicker
import com.mindsync.mindmapview.desktop.component.PICKER_LABEL_WIDTH
import com.mindsync.mindmapview.desktop.label
import com.mindsync.mindmapview.desktop.parseHexColor
import com.mindsync.mindmapview.desktop.toHex

// Wide enough for the longest of the picker labels, so the pickers line up.
private val PREFERENCES_DIALOG_SIZE = DpSize(720.dp, 700.dp)
private val PREFERENCES_LABEL_WIDTH = 128.dp
private val EDITOR_BUTTON_WIDTH = 140.dp

/**
 * What the user chose in the preferences dialog. Each of them starts out as the app was before there were
 * any preferences.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // The shape of a node that is added to a parent without children, which has no last child to look like.
    val newNodeShape: NodeShape = NodeShape.RECTANGLE,
    val isRemoveConfirmed: Boolean = true,
    // The fill colors offered when a node is added or edited.
    val nodeColors: List<Color> = DEFAULT_NODE_COLORS,
    // The text colors offered when a node is added or edited.
    val textColors: List<Color> = DEFAULT_TEXT_COLORS,
)

// The preferences take effect when they are confirmed, as the changes of the other dialogs do.
@Composable
fun PreferencesDialog(
    preferences: UserPreferences,
    onSubmit: (UserPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    var edited by remember { mutableStateOf(preferences) }

    DialogWindow(
        onCloseRequest = onDismiss
        , state = rememberDialogState(size = PREFERENCES_DIALOG_SIZE)
        , title = "Preferences"
        , resizable = false
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
                , modifier = Modifier.fillMaxSize().padding(24.dp)
            ) {
                ChoicePicker(
                    label = "Theme"
                    , choices = ThemeMode.entries
                    , choiceLabel = { it.label }
                    , selectedChoice = edited.themeMode
                    , onSelect = { edited = edited.copy(themeMode = it) }
                    , labelWidth = PREFERENCES_LABEL_WIDTH
                )
                ChoicePicker(
                    label = "New node shape"
                    , choices = NodeShape.entries
                    , choiceLabel = { it.label }
                    , selectedChoice = edited.newNodeShape
                    , onSelect = { edited = edited.copy(newNodeShape = it) }
                    , labelWidth = PREFERENCES_LABEL_WIDTH
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                    , modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { edited = edited.copy(isRemoveConfirmed = !edited.isRemoveConfirmed) }
                        .padding(end = 12.dp)
                ) {
                    Checkbox(
                        checked = edited.isRemoveConfirmed
                        , onCheckedChange = null
                        , modifier = Modifier.padding(8.dp)
                    )
                    Text("Ask before removing a node")
                }
                ColorListsEditor(
                    listOf(
                        EditableColors(
                            label = "Fill colors"
                            , colors = edited.nodeColors
                            , defaults = DEFAULT_NODE_COLORS
                            , onChange = { edited = edited.copy(nodeColors = it) }
                        )
                        , EditableColors(
                            label = "Text colors"
                            , colors = edited.textColors
                            , defaults = DEFAULT_TEXT_COLORS
                            , onChange = { edited = edited.copy(textColors = it) }
                        )
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    , verticalAlignment = Alignment.Bottom
                    , modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSubmit(edited)
                            onDismiss()
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
fun <T> ChoicePicker(
    label: String,
    choices: List<T>,
    choiceLabel: (T) -> String,
    selectedChoice: T,
    onSelect: (T) -> Unit,
    labelWidth: Dp = PICKER_LABEL_WIDTH,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(labelWidth))
        choices.forEach { choice ->
            val isSelected = choice == selectedChoice
            val borderColor =
                if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            Text(
                choiceLabel(choice),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(if (isSelected) 3.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable { onSelect(choice) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

// A list of colors the user can change, with the colors it goes back to when it is reset.
private class EditableColors(
    val label: String
    , val colors: List<Color>
    , val defaults: List<Color>
    , val onChange: (List<Color>) -> Unit
)

/**
 * Lets the user change lists of colors: a color is picked from one of the lists and then changed with the
 * color picker or its hex value, removed, or copied as a new color at the end of its list.
 */
@Composable
private fun ColorListsEditor(
    lists: List<EditableColors>
) {
    var selectedList by remember { mutableStateOf(0) }
    var selectedIndex by remember { mutableStateOf(0) }
    val list = lists[selectedList]
    val colors = list.colors
    val index = selectedIndex.coerceIn(colors.indices)
    // The text is kept apart from the color, so a hex value that is still being typed is not lost.
    var hexText by remember(selectedList, index, colors[index]) { mutableStateOf(colors[index].toHex()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lists.forEachIndexed { listIndex, editable ->
            ColorPicker(
                label = editable.label
                , colors = editable.colors
                // Only the list the selected color is in shows it as selected.
                , selectedColor = if (listIndex == selectedList) colors[index] else Color.Unspecified
                , onSelect = {
                    selectedList = listIndex
                    selectedIndex = editable.colors.indexOf(it)
                }
                , labelWidth = PREFERENCES_LABEL_WIDTH
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
            , modifier = Modifier.padding(start = PREFERENCES_LABEL_WIDTH + 8.dp)
        ) {
            val replaceSelected = { color: Color -> list.onChange(colors.toMutableList().also { it[index] = color }) }
            HsvColorPicker(
                color = colors[index]
                , onColorChange = replaceSelected
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hexText
                    , onValueChange = { text ->
                        hexText = text
                        parseHexColor(text)?.let(replaceSelected)
                    }
                    , label = { Text("Hex") }
                    , isError = parseHexColor(hexText) == null
                    , singleLine = true
                    , modifier = Modifier.width(EDITOR_BUTTON_WIDTH)
                )
                OutlinedButton(
                    onClick = {
                        list.onChange(colors + colors[index])
                        selectedIndex = colors.size
                    }
                    , modifier = Modifier.width(EDITOR_BUTTON_WIDTH)
                ) {
                    Text("Add")
                }
                // A picker needs a color to offer, so the last one of a list stays.
                OutlinedButton(
                    onClick = { list.onChange(colors.filterIndexed { i, _ -> i != index }) }
                    , enabled = colors.size > 1
                    , modifier = Modifier.width(EDITOR_BUTTON_WIDTH)
                ) {
                    Text("Remove")
                }
                OutlinedButton(
                    onClick = {
                        list.onChange(list.defaults)
                        selectedIndex = 0
                    }
                    , modifier = Modifier.width(EDITOR_BUTTON_WIDTH)
                ) {
                    Text("Reset")
                }
            }
        }
    }
}
