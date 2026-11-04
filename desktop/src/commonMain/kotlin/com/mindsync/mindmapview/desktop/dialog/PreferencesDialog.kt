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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import com.mindsync.library.model.LevelStyle
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.NoteDisplay
import com.mindsync.library.model.TopicKind
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.DEFAULT_ARROW_LINK_COLORS
import com.mindsync.mindmapview.desktop.DEFAULT_NODE_COLORS
import com.mindsync.mindmapview.desktop.DEFAULT_TEXT_COLORS
import com.mindsync.mindmapview.desktop.NodeFont
import com.mindsync.mindmapview.desktop.component.BorderSettings
import com.mindsync.mindmapview.desktop.component.COLOR_EDITOR_BUTTON_WIDTH
import com.mindsync.mindmapview.desktop.component.CollapsibleSection
import com.mindsync.mindmapview.desktop.component.ColorEditor
import com.mindsync.mindmapview.desktop.component.ColorPicker
import com.mindsync.mindmapview.desktop.component.DialogLayout
import com.mindsync.mindmapview.desktop.component.DropdownPicker
import com.mindsync.mindmapview.desktop.component.PICKER_LABEL_WIDTH
import com.mindsync.mindmapview.desktop.label
import com.mindsync.mindmapview.desktop.loadNodeFontFamily
import com.mindsync.mindmapview.desktop.themeArrowLinkColors
import com.mindsync.mindmapview.desktop.withTheme

// Wide enough for the longest of the picker labels, so the pickers line up.
private val PREFERENCES_DIALOG_SIZE = DpSize(720.dp, 840.dp)
private val PREFERENCES_LABEL_WIDTH = 128.dp

/**
 * What the user chose in the preferences dialog. Each of them starts out as
 * the app was before there were any preferences.
 *
 * Represents user-configurable preferences for a mind map application.
 *
 * @property arrowLinkColors The colors available for arrow links within the mind map.
 * @property backgroundColor The color maps are drawn on, or null for the background of their theme.
 * @property emojiTones The mapping of base emojis to their chosen skin tones.
 * @property isRemoveConfirmed Whether the removal of elements requires confirmation.
 * @property mapThemeId The identifier of the theme applied to new maps or the last selected theme.
 * @property rootTopic How the root topic is drawn, and the colors offered for it.
 * @property subtopic How the subtopics are drawn and added, and the colors offered for them.
 * @property topic How the topics are drawn and added, and the colors offered for them.
 */
data class UserPreferences(
    // The colors offered for an arrow link.
    val arrowLinkColors: List<Color> = DEFAULT_ARROW_LINK_COLORS
    // The color maps are drawn on in place of their theme's background, or null for the theme's.
    , val backgroundColor: Color? = null
    // The skin tone chosen for an emoji, keyed by the base emoji it is a tone of.
    , val emojiTones: Map<String, String> = emptyMap()
    , val isRemoveConfirmed: Boolean = true
    // The id of the theme new maps start in, which is also the one last chosen for an open map.
    , val mapThemeId: String = MapThemes.CLASSIC.id
    , val rootTopic: TopicPreferences = TopicPreferences()
    , val subtopic: TopicPreferences = TopicPreferences()
    , val topic: TopicPreferences = TopicPreferences()
) {
    /** Returns the preferences of the nodes of [kind]. */
    fun topicOf(
        kind: TopicKind
    ): TopicPreferences = when (kind) {
        TopicKind.ROOT_TOPIC -> rootTopic
        TopicKind.TOPIC -> topic
        TopicKind.SUBTOPIC -> subtopic
    }

    /** Returns these preferences with [preferences] for the nodes of [kind]. */
    fun withTopic(
        kind: TopicKind
        , preferences: TopicPreferences
    ): UserPreferences = when (kind) {
        TopicKind.ROOT_TOPIC -> copy(rootTopic = preferences)
        TopicKind.TOPIC -> copy(topic = preferences)
        TopicKind.SUBTOPIC -> copy(subtopic = preferences)
    }

    /** How each kind of topic is drawn, in place of the theme's style for its level. */
    val topicStyles: Map<TopicKind, LevelStyle>
        get() = TopicKind.entries.associateWith { topicOf(it).style }

    /** How the notes of each kind of topic are shown, where a node has no way of its own. */
    val topicNoteDisplays: Map<TopicKind, NoteDisplay>
        get() = TopicKind.entries.associateWith { topicOf(it).noteDisplay }

    /** How the attributes of each kind of topic are shown, where a node has no way of its own. */
    val topicAttributeDisplays: Map<TopicKind, AttributeDisplay>
        get() = TopicKind.entries.associateWith { topicOf(it).attributeDisplay }
}

/**
 * What the user chose for one kind of topic: how its nodes are drawn where they have no colors or border of their
 * own, the shape one is added in, and the colors offered for one when it is added or edited.
 *
 * @property attributeDisplay How the attributes of a node of this kind are shown, where it has no way of its own.
 * @property font The font the text of a node of this kind is drawn in.
 * @property shape The shape a node of this kind is added in, where it has no sibling to look like. The root stays
 * the oval it is.
 * @property style The fill, text color and border nodes of this kind are drawn in. A null fill or text color
 * leaves each node in the color of its shape, as the Classic theme does.
 * @property noteDisplay How the note of a node of this kind is shown, where it has no way of its own.
 * @property fillColors The fill colors offered for a node of this kind.
 * @property textColors The text colors offered for a node of this kind.
 */
data class TopicPreferences(
    val attributeDisplay: AttributeDisplay = AttributeDisplay.INLINE
    , val font: NodeFont = NodeFont.DEFAULT
    , val noteDisplay: NoteDisplay = NoteDisplay.FLYOVER
    , val shape: NodeShape = NodeShape.RECTANGLE
    , val style: LevelStyle = LevelStyle(fill = null, text = null)
    , val fillColors: List<Color> = DEFAULT_NODE_COLORS
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
    // The color sections are closed to start with, so the other settings are seen at a glance.
    var isBackgroundColorOpen by remember { mutableStateOf(false) }
    var openTopics by remember { mutableStateOf(emptySet<TopicKind>()) }
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
                        // What the theme sets takes the place of what was set before.
                        , onSelect = { theme ->
                            if (theme.id != edited.mapThemeId) {
                                edited = edited.withTheme(theme).copy(mapThemeId = theme.id)
                            }
                        }
                        , labelWidth = PREFERENCES_LABEL_WIDTH
                    )
                    CollapsibleSection(
                        title = "Background color"
                        , isOpen = isBackgroundColorOpen
                        , onToggle = { isBackgroundColorOpen = !isBackgroundColorOpen }
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            BackgroundColorEditor(
                                color = edited.backgroundColor
                                , themeBackground = MapThemes.byId(edited.mapThemeId).background
                                , onChange = { edited = edited.copy(backgroundColor = it) }
                            )
                        }
                    }
                    // Each kind of topic is set apart, as a theme draws each in a style of its own.
                    TopicKind.entries.forEach { kind ->
                        CollapsibleSection(
                            title = kind.label
                            , isOpen = kind in openTopics
                            , onToggle = {
                                openTopics = if (kind in openTopics) openTopics - kind else openTopics + kind
                            }
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                TopicEditor(
                                    kind = kind
                                    , topic = edited.topicOf(kind)
                                    , fontFamilies = fontFamilies
                                    , themed = edited.topicOf(kind).withTheme(MapThemes.byId(edited.mapThemeId), kind)
                                    , onChange = { edited = edited.withTopic(kind, it) }
                                )
                            }
                        }
                    }
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
                                        , defaults = themeArrowLinkColors(MapThemes.byId(edited.mapThemeId))
                                        , onChange = { edited = edited.copy(arrowLinkColors = it) }
                                    )
                                )
                            )
                        }
                    }
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
        val replaceSelected = { color: Color -> list.onChange(colors.toMutableList().also { it[index] = color }) }
        ColorEditor(
            color = colors[index]
            , onColorChange = replaceSelected
            , modifier = Modifier.padding(start = PREFERENCES_LABEL_WIDTH + 8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    list.onChange(colors + colors[index])
                    selectedIndex = colors.size
                }
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Add")
            }
            // A picker needs a color to offer, so the last one of a list stays.
            OutlinedButton(
                onClick = { list.onChange(colors.filterIndexed { i, _ -> i != index }) }
                , enabled = colors.size > 1
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Remove")
            }
            OutlinedButton(
                onClick = {
                    list.onChange(list.defaults)
                    selectedIndex = 0
                }
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Reset")
            }
        }
    }
}

/**
 * Lets the user choose the one [color] maps are drawn on: one of the backgrounds of the themes, or any other with
 * the color picker or its hex value. Null is the background of the map's theme, [themeBackground], which Reset
 * goes back to.
 */
@Composable
private fun BackgroundColorEditor(
    color: Color?
    , themeBackground: Color
    , onChange: (Color?) -> Unit
) {
    val shown = color ?: themeBackground
    // A color of the user's own is offered with those of the themes, so it shows as the one chosen.
    val choices = (MapThemes.all.map { it.background } + shown).distinct()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorPicker(
            label = "Background"
            , colors = choices
            , selectedColor = shown
            , onSelect = onChange
            , labelWidth = PREFERENCES_LABEL_WIDTH
        )
        ColorEditor(
            color = shown
            , onColorChange = onChange
            , modifier = Modifier.padding(start = PREFERENCES_LABEL_WIDTH + 8.dp)
        ) {
            OutlinedButton(
                onClick = { onChange(null) }
                , enabled = color != null
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Reset")
            }
        }
    }
}

// The swatch for no color of the topic's own, which leaves each node in the color of its shape.
private val SHAPE_COLOR = Color.Transparent

/**
 * Lets the user choose how the nodes of [kind] are drawn and added: the font of their text, their shape, but for
 * the root, which stays an oval, how their notes and attributes are shown, their fill and text colors, from the colors offered for them or any other with the
 * color picker, the colors offered, and their border. Reset goes back to what the theme sets, [themed].
 */
@Composable
private fun TopicEditor(
    kind: TopicKind
    , topic: TopicPreferences
    // Each font's name is drawn in that font once it has loaded.
    , fontFamilies: Map<NodeFont, FontFamily>
    , themed: TopicPreferences
    , onChange: (TopicPreferences) -> Unit
) {
    // Which row of colors the color picker changes: the text's, or the fill's.
    var isTextEdited by remember { mutableStateOf(false) }
    val style = topic.style
    val fill = style.fill ?: SHAPE_COLOR
    val text = style.text ?: SHAPE_COLOR
    val setFill = { color: Color ->
        onChange(topic.copy(style = style.copy(fill = color.takeIf { it != SHAPE_COLOR })))
    }
    val setText = { color: Color ->
        onChange(topic.copy(style = style.copy(text = color.takeIf { it != SHAPE_COLOR })))
    }
    val edited = if (isTextEdited) text else fill
    val offered = if (isTextEdited) topic.textColors else topic.fillColors
    val setOffered = { colors: List<Color> ->
        onChange(if (isTextEdited) topic.copy(textColors = colors) else topic.copy(fillColors = colors))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownPicker(
            label = "Text font"
            , choices = NodeFont.entries
            , choiceLabel = { it.label }
            , selectedChoice = topic.font
            , onSelect = { onChange(topic.copy(font = it)) }
            , labelWidth = PREFERENCES_LABEL_WIDTH
            , choiceFontFamily = { fontFamilies[it] }
        )
        if (kind != TopicKind.ROOT_TOPIC) {
            DropdownPicker(
                label = "Shape"
                , choices = NodeShape.entries
                , choiceLabel = { it.label }
                , selectedChoice = topic.shape
                , onSelect = { onChange(topic.copy(shape = it)) }
                , labelWidth = PREFERENCES_LABEL_WIDTH
            )
        }
        DropdownPicker(
            label = "Notes"
            , choices = NoteDisplay.entries
            , choiceLabel = { it.label }
            , selectedChoice = topic.noteDisplay
            , onSelect = { onChange(topic.copy(noteDisplay = it)) }
            , labelWidth = PREFERENCES_LABEL_WIDTH
        )
        DropdownPicker(
            label = "Attributes"
            , choices = AttributeDisplay.entries
            , choiceLabel = { it.label }
            , selectedChoice = topic.attributeDisplay
            , onSelect = { onChange(topic.copy(attributeDisplay = it)) }
            , labelWidth = PREFERENCES_LABEL_WIDTH
        )
        // The colors offered come after the topic's own, which is offered as well where it is not one of them.
        ColorPicker(
            label = "Fill"
            , colors = (listOf(SHAPE_COLOR) + topic.fillColors + fill).distinct()
            // Only the row the color picker changes shows its color as chosen.
            , selectedColor = if (isTextEdited) Color.Unspecified else fill
            , onSelect = {
                isTextEdited = false
                setFill(it)
            }
            , labelWidth = PREFERENCES_LABEL_WIDTH
        )
        ColorPicker(
            label = "Text"
            , colors = (listOf(SHAPE_COLOR) + topic.textColors + text).distinct()
            , selectedColor = if (isTextEdited) text else Color.Unspecified
            , onSelect = {
                isTextEdited = true
                setText(it)
            }
            , labelWidth = PREFERENCES_LABEL_WIDTH
        )
        ColorEditor(
            color = edited
            , onColorChange = { if (isTextEdited) setText(it) else setFill(it) }
            , modifier = Modifier.padding(start = PREFERENCES_LABEL_WIDTH + 8.dp)
        ) {
            // The color chosen is added to the colors offered for a node of this kind, or taken out of them.
            OutlinedButton(
                onClick = { setOffered(offered + edited) }
                , enabled = edited != SHAPE_COLOR && edited !in offered
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Add")
            }
            // A picker needs a color to offer, so the last one of a list stays.
            OutlinedButton(
                onClick = { setOffered(offered - edited) }
                , enabled = edited in offered && offered.size > 1
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Remove")
            }
            OutlinedButton(
                onClick = { onChange(themed) }
                , enabled = topic != themed
                , modifier = Modifier.width(COLOR_EDITOR_BUTTON_WIDTH)
            ) {
                Text("Reset")
            }
        }
        Text(
            "The empty circle leaves each node in the color of its shape. Add and Remove change the colors offered "
                + "when a node is added or edited."
            , style = MaterialTheme.typography.labelSmall
        )
        HorizontalDivider()
        Text("Border", style = MaterialTheme.typography.titleSmall)
        BorderSettings(
            border = style.border
            , colors = (topic.fillColors + topic.textColors + style.border.color).distinct()
            , onChange = { onChange(topic.copy(style = style.copy(border = it))) }
        )
    }
}
