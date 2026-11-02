package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeShape
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.NoteDisplay
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.DEFAULT_ARROW_LINK_COLORS
import com.mindsync.mindmapview.desktop.DEFAULT_NODE_COLORS
import com.mindsync.mindmapview.desktop.DEFAULT_TEXT_COLORS
import com.mindsync.mindmapview.desktop.NodeFont
import com.mindsync.mindmapview.desktop.component.CollapsibleSection
import com.mindsync.mindmapview.desktop.component.ColorPicker
import com.mindsync.mindmapview.desktop.component.DialogLayout
import com.mindsync.mindmapview.desktop.component.DropdownPicker
import com.mindsync.mindmapview.desktop.component.HsvColorPicker
import com.mindsync.mindmapview.desktop.component.PICKER_LABEL_WIDTH
import com.mindsync.mindmapview.desktop.emojiVisualTransformation
import com.mindsync.mindmapview.desktop.label
import com.mindsync.mindmapview.desktop.loadNodeFontFamily
import com.mindsync.mindmapview.desktop.parseHexColor
import com.mindsync.mindmapview.desktop.themeFillColors
import com.mindsync.mindmapview.desktop.themeTextColors
import com.mindsync.mindmapview.desktop.toHex

// Wide enough for the longest of the picker labels, so the pickers line up.
private val PREFERENCES_DIALOG_SIZE = DpSize(720.dp, 840.dp)
private val PREFERENCES_LABEL_WIDTH = 128.dp
private val EDITOR_BUTTON_WIDTH = 140.dp

/**
 * What the user chose in the preferences dialog. Each of them starts out as
 * the app was before there were any preferences.
 *
 * Represents user-configurable preferences for a mind map application.
 *
 * @property arrowLinkColors The colors available for arrow links within the mind map.
 * @property attributeDisplay The mode of displaying the attributes of nodes, for a map theme without its own.
 * @property emojiTones The mapping of base emojis to their chosen skin tones.
 * @property isRemoveConfirmed Whether the removal of elements requires confirmation.
 * @property mapThemeId The identifier of the theme applied to new maps or the last selected theme.
 * @property newNodeShape The default shape applied to newly created nodes in the mind map.
 * @property nodeFont The font used to render text inside nodes.
 * @property nodeColors The colors available for filling nodes during creation or editing.
 * @property noteDisplay The mode of displaying notes associated with nodes in the mind map.
 * @property textColors The colors available for text within nodes during creation or editing.
 */
data class UserPreferences(
    // The colors offered for an arrow link.
    val arrowLinkColors: List<Color> = DEFAULT_ARROW_LINK_COLORS
    // How the attributes of a node are shown, unless the map's theme has a way of its own.
    , val attributeDisplay: AttributeDisplay = AttributeDisplay.INLINE
    // The skin tone chosen for an emoji, keyed by the base emoji it is a tone of.
    , val emojiTones: Map<String, String> = emptyMap()
    // The shape of a node that is added to a parent without children, which has no last child to look like.
    , val isRemoveConfirmed: Boolean = true
    // The id of the theme new maps start in, which is also the one last chosen for an open map.
    , val mapThemeId: String = MapThemes.CLASSIC.id
    , val newNodeShape: NodeShape = NodeShape.RECTANGLE
    // The fill colors offered when a node is added or edited.
    , val nodeColors: List<Color> = DEFAULT_NODE_COLORS
    // The font node text is drawn in.
    , val nodeFont: NodeFont = NodeFont.DEFAULT
    // How the note of a node is shown.
    , val noteDisplay: NoteDisplay = NoteDisplay.FLYOVER
    // The text colors offered when a node is added or edited.
    , val textColors: List<Color> = DEFAULT_TEXT_COLORS
)

// The preferences take effect when they are confirmed, as the changes of the other dialogs do.
@Composable
fun PreferencesDialog(
    preferences: UserPreferences
    , onSubmit: (UserPreferences) -> Unit
    , onDismiss: () -> Unit
) {
    var edited by remember { mutableStateOf(preferences) }
    // Closed to start with, as arrow link colors are changed less often than those of nodes.
    var isArrowLinkColorsOpen by remember { mutableStateOf(false) }
    // Each font's name is drawn in that font once it has loaded.
    var fontFamilies by remember { mutableStateOf<Map<NodeFont, FontFamily>>(emptyMap()) }
    LaunchedEffect(Unit) {
        NodeFont.entries.forEach { font ->
            loadNodeFontFamily(font)?.let { fontFamilies = fontFamilies + (font to it) }
        }
    }

    val dialogState = remember { AppDialogState(PREFERENCES_DIALOG_SIZE) }
    AppDialog(
        title = "Preferences"
        , state = dialogState
        , onCloseRequest = onDismiss
        , isResizable = false
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // The settings scroll where the window is too small for them, and the buttons stay at the bottom.
            DialogLayout(
                noteMinHeight = 0.dp
                , gap = 16.dp
                , contentPadding = PaddingValues(24.dp)
                , onContentMinHeightChange = { dialogState.contentMinHeight = it }
                , settings = {
                    DropdownPicker(
                        label = "Map theme"
                        , choices = MapThemes.all
                        , choiceLabel = { it.name }
                        , selectedChoice = MapThemes.byId(edited.mapThemeId)
                        // The colors offered for nodes go with the theme, in place of those of the last one.
                        , onSelect = { theme ->
                            if (theme.id != edited.mapThemeId) {
                                edited = edited.copy(
                                    mapThemeId = theme.id
                                    , nodeColors = themeFillColors(theme)
                                    , textColors = themeTextColors(theme)
                                )
                            }
                        }
                        , labelWidth = PREFERENCES_LABEL_WIDTH
                    )
                    DropdownPicker(
                        label = "New node shape"
                        , choices = NodeShape.entries
                        , choiceLabel = { it.label }
                        , selectedChoice = edited.newNodeShape
                        , onSelect = { edited = edited.copy(newNodeShape = it) }
                        , labelWidth = PREFERENCES_LABEL_WIDTH
                    )
                    DropdownPicker(
                        label = "Classic font"
                        , choices = NodeFont.entries
                        , choiceLabel = { it.label }
                        , selectedChoice = edited.nodeFont
                        , onSelect = { edited = edited.copy(nodeFont = it) }
                        , labelWidth = PREFERENCES_LABEL_WIDTH
                        , choiceFontFamily = { fontFamilies[it] }
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
                                , defaults = themeFillColors(MapThemes.byId(edited.mapThemeId))
                                , onChange = { edited = edited.copy(nodeColors = it) }
                            )
                            , EditableColors(
                                label = "Text colors"
                                , colors = edited.textColors
                                , defaults = themeTextColors(MapThemes.byId(edited.mapThemeId))
                                , onChange = { edited = edited.copy(textColors = it) }
                            )
                        )
                    )
                    CollapsibleSection(
                        title = "Arrow link colors"
                        , isOpen = isArrowLinkColorsOpen
                        , onToggle = { isArrowLinkColorsOpen = !isArrowLinkColorsOpen }
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            ColorListsEditor(
                                listOf(
                                    EditableColors(
                                        label = "Colors"
                                        , colors = edited.arrowLinkColors
                                        , defaults = DEFAULT_ARROW_LINK_COLORS
                                        , onChange = { edited = edited.copy(arrowLinkColors = it) }
                                    )
                                )
                            )
                        }
                    }
                    DropdownPicker(
                        label = "Notes"
                        , choices = NoteDisplay.entries
                        , choiceLabel = { it.label }
                        , selectedChoice = edited.noteDisplay
                        , onSelect = { edited = edited.copy(noteDisplay = it) }
                        , labelWidth = PREFERENCES_LABEL_WIDTH
                    )
                    DropdownPicker(
                        label = "Attributes"
                        , choices = AttributeDisplay.entries
                        , choiceLabel = { it.label }
                        , selectedChoice = edited.attributeDisplay
                        , onSelect = { edited = edited.copy(attributeDisplay = it) }
                        , labelWidth = PREFERENCES_LABEL_WIDTH
                    )
                }
                , note = null
                , buttons = {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            onSubmit(edited)
                            onDismiss()
                        }
                    ) {
                        Text("Save & Close")
                    }
                }
            )
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
    choiceFontFamily: (T) -> FontFamily? = { null },
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(labelWidth))
        // The choices go on more lines when they do not fit on one.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { choice ->
                val isSelected = choice == selectedChoice
                val borderColor =
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                Text(
                    choiceLabel(choice),
                    fontFamily = choiceFontFamily(choice),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (isSelected) 3.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable { onSelect(choice) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
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
                    , visualTransformation = emojiVisualTransformation()
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
