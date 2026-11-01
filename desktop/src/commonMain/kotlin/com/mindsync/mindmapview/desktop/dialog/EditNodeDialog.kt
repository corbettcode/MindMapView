package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeShape
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.mindmapview.desktop.ThemeSwatch
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.EDIT_DIALOG_MIN_WIDTH
import com.mindsync.mindmapview.desktop.EDIT_DIALOG_SIZE
import com.mindsync.mindmapview.desktop.NOTE_LINES
import com.mindsync.mindmapview.desktop.ShowEmojisInColor
import com.mindsync.mindmapview.desktop.TEXT_FIELD_VERTICAL_PADDING
import com.mindsync.mindmapview.desktop.UrlImageLoader
import com.mindsync.mindmapview.desktop.component.AttributesEditor
import com.mindsync.mindmapview.desktop.component.ColorPicker
import com.mindsync.mindmapview.desktop.component.attributeRowsOf
import com.mindsync.mindmapview.desktop.component.attributesToKeep
import com.mindsync.mindmapview.desktop.component.BorderSettings
import com.mindsync.mindmapview.desktop.component.CollapsibleSection
import com.mindsync.mindmapview.desktop.component.DialogLayout
import com.mindsync.mindmapview.desktop.component.EmojiPicker
import com.mindsync.mindmapview.desktop.component.LineSettings
import com.mindsync.mindmapview.desktop.component.RichTextToggle
import com.mindsync.mindmapview.desktop.component.RichTextToolbar
import com.mindsync.mindmapview.desktop.component.ShapePicker
import com.mindsync.mindmapview.desktop.emojiVisualTransformation
import com.mindsync.mindmapview.desktop.noteFromMarkdown
import com.mindsync.mindmapview.desktop.noteHtml
import com.mindsync.mindmapview.desktop.noteToMarkdown
import com.mindsync.mindmapview.desktop.noteWithImages
import com.mohamedrejeb.richeditor.model.LocalImageLoader
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import kotlinx.coroutines.launch


// The room between the dialog's rows.
private val DIALOG_GAP = 16.dp

/**
 * Displays a dialog that allows editing a node element with customization options for its description,
 * appearance, and additional note content. The dialog is designed to handle various input fields such
 * as text, colors, shapes, and emojis, along with providing a rich text editor for enhanced formatting.
 *
 * @param title The title of the dialog.
 * @param description The initial description of the node being edited.
 * @param isDescriptionSelected Whether the description text should be preselected.
 * @param note The note content associated with the node being edited.
 * @param shape The predefined shape of the node, if applicable.
 * @param color The fill color of the node.
 * @param textColor The text color for the node.
 * @param line The node's own line settings, whose null values are inherited from its parent.
 * @param border What is drawn around the node: nothing, or a line of a width and color.
 * @param nodeColors A list of colors available for selecting the node's fill color.
 * @param textColors A list of colors available for selecting the node's text color.
 * @param mapEmojis A list of emoji strings available for selection and customization within the dialog.
 * @param emojiTones A map that associates emojis to their tonal variations.
 * @param onToneChange A callback triggered when the tone of an emoji is changed. Receives the base emoji
 * and its selected tonal variant.
 * @param onSubmit A callback triggered upon confirming the dialog. Receives the updated description,
 * note, shape, fill color, text color, line settings, and border of the node.
 * @param onDismiss A callback triggered when the dialog is dismissed.
 * @param onAddAnother A callback triggered after the node is submitted with "Add Another", for another node to be
 * added. Without it, the dialog has no such button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeDialog(
    title: String
    , description: String
    , isDescriptionSelected: Boolean
    , note: String
    , shape: NodeShape?
    , color: Color
    , textColor: Color
    , line: NodeLine
    , border: NodeBorder
    , nodeColors: List<Color>
    , textColors: List<Color>
    // The colors the root starts with, which follow the map's theme; a node with a shape starts with the shape's.
    , rootColor: Color?
    , rootTextColor: Color?
    // How the map's theme draws a node of this one's level that follows it: null where the theme leaves the
    // node's own colors, as Classic does. A line node's text is drawn on the background, in [themeLineNodeText].
    , themeFill: Color?
    , themeText: Color?
    , themeLineNodeText: Color?
    , attributes: List<NodeAttribute>
    // The names other nodes have attributes of, to add one of at a click.
    , knownAttributeNames: List<String>
    // Whether the attributes are open to start with, as when they are what the dialog was opened for.
    , isAttributesOpenOnStart: Boolean
    , mapEmojis: List<String>
    , emojiTones: Map<String, String>
    , onToneChange: (base: String, variant: String) -> Unit
    , onSubmit: (
        description: String
        , note: String
        , shape: NodeShape?
        , color: Color
        , textColor: Color
        , line: NodeLine
        , border: NodeBorder
        , attributes: List<NodeAttribute>
    ) -> Unit
    , onDismiss: () -> Unit
    , onAddAnother: (() -> Unit)? = null
) {
    // The emoji in front of the description is picked on its own, so it is kept out of the editor.
    val (emoji, text) = remember { DescriptionEmoji.split(description) }
    val richTextState = remember {
        RichTextState().setHtml(text).apply {
            if (isDescriptionSelected) selection = TextRange(0, annotatedString.text.length)
        }
    }
    val descriptionFocusRequester = remember { FocusRequester() }
    // Made once the images of the note are loaded, as an image that is loaded later does not show.
    var noteState by remember { mutableStateOf<RichTextState?>(null) }
    LaunchedEffect(Unit) { noteState = noteWithImages(note) }
    val scope = rememberCoroutineScope()
    // The note as Markdown while it is shown so, for markup to be typed that takes effect.
    var noteMarkdown by remember { mutableStateOf<String?>(null) }
    var selectedEmoji by remember { mutableStateOf(emoji) }
    var selectedShape by remember { mutableStateOf(shape) }
    var selectedColor by remember { mutableStateOf(color) }
    var selectedTextColor by remember { mutableStateOf(textColor) }
    // The colors a node starts with follow the map's theme, so they are shown as the theme draws them.
    val defaultColor = selectedShape?.defaultColor ?: rootColor
    val defaultTextColor = selectedShape?.defaultTextColor ?: rootTextColor
    val fillSwatch = defaultColor?.let { ThemeSwatch(it, themeFill) }
    // A node with a fill of its own keeps its text color, as the theme's is made for the theme's fill.
    val isFillThemed = selectedColor == defaultColor
    val textSwatch = defaultTextColor?.let {
        ThemeSwatch(it, if (selectedShape == NodeShape.LINE) themeLineNodeText else themeText.takeIf { isFillThemed })
    }
    val shownColor = fillSwatch?.shown(selectedColor) ?: selectedColor
    val shownTextColor = textSwatch?.shown(selectedTextColor) ?: selectedTextColor
    var selectedLine by remember { mutableStateOf(line) }
    // A border that is turned on starts in the color of the text, but is not changed along with it after that.
    var selectedBorder by remember { mutableStateOf(if (border.isNone) border.copy(color = textColor) else border) }
    // Closed unless the node has a line setting of its own, so the dialog fits on a small screen.
    var isLineOpen by remember { mutableStateOf(!line.isEmpty) }
    // Closed unless the node has a border, for the same reason.
    var isBorderOpen by remember { mutableStateOf(!border.isNone) }
    // Open when the node has a note, as the mind map marks it.
    var isNoteOpen by remember { mutableStateOf(note.isNotBlank()) }
    // Open when the node has attributes, as the mind map marks them, or when they are what the dialog is for.
    var isAttributesOpen by remember { mutableStateOf(isAttributesOpenOnStart || attributes.isNotEmpty()) }
    val attributeRows = remember { mutableStateListOf(*attributeRowsOf(attributes).toTypedArray()) }
    var isAttributesFocused by remember { mutableStateOf(false) }
    // Opened for its attributes, a node without any starts in the name of its empty row, to type one in at once.
    val startsInAttributes = isAttributesOpenOnStart && attributes.isEmpty()

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineStyle = LocalTextStyle.current
    val noteFieldMinHeight = remember(density, lineStyle) {
        val lines = textMeasurer.measure("\n".repeat(NOTE_LINES - 1), lineStyle).size.height
        with(density) { lines.toDp() } + TEXT_FIELD_VERTICAL_PADDING
    }
    // The note's section is its field and what frames it: the title, the toolbar and the padding. That does not
    // change with the field's height, so it is the section's height less the field's.
    var noteSectionHeight by remember { mutableStateOf(0.dp) }
    var noteFieldHeight by remember { mutableStateOf(0.dp) }
    val noteMinHeight = noteFieldMinHeight + (noteSectionHeight - noteFieldHeight).coerceAtLeast(0.dp)
    // Its least width; its height is fitted to what its content needs.
    val dialogState = remember { AppDialogState(EDIT_DIALOG_SIZE, minSize = DpSize(EDIT_DIALOG_MIN_WIDTH, 0.dp)) }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var isNoteFocused by remember { mutableStateOf(false) }

    fun save() {
        onSubmit(
            DescriptionEmoji.prepend(selectedEmoji, richTextState.toHtml())
            , noteMarkdown?.let { noteFromMarkdown(it) } ?: noteState?.let { noteHtml(it) } ?: note
            , selectedShape
            , selectedColor
            , selectedTextColor
            , selectedLine
            , selectedBorder
            , attributesToKeep(attributeRows)
        )
    }

    fun submit() {
        save()
        onDismiss()
    }

    // A window of its own, so it can be resized: the note takes the room the rest leaves, which is three lines
    // to start with and at the least.
    AppDialog(
        title = title
        , state = dialogState
        , onCloseRequest = onDismiss
    ) {
        LaunchedEffect(Unit) {
            if (!startsInAttributes) descriptionFocusRequester.requestFocus()
        }
        // Enter clicks Save & Close, wherever the focus is but in the note, whose lines are typed with Enter. A line
        // of the node's text is typed with Shift+Enter or Alt+Enter instead.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    when {
                        event.key != Key.Enter && event.key != Key.NumPadEnter -> false
                        isNoteFocused || isAttributesFocused -> false
                        event.type != KeyEventType.KeyDown -> true
                        event.isShiftPressed || event.isAltPressed -> {
                            if (isDescriptionFocused) richTextState.replaceSelectedText("\n")
                            true
                        }
                        else -> {
                            submit()
                            true
                        }
                    }
                }
        ) {
            DialogLayout(
                noteMinHeight = noteMinHeight
                , gap = DIALOG_GAP
                , contentPadding = PaddingValues(24.dp)
                , onContentMinHeightChange = { dialogState.contentMinHeight = it }
                , settings = {
                    selectedShape?.let { currentShape ->
                        ShapePicker(
                            selectedShape = currentShape
                            , onSelect = { newShape ->
                                // A color that was not chosen is that of the shape, so it goes along with the shape.
                                if (selectedColor == currentShape.defaultColor) selectedColor = newShape.defaultColor
                                if (selectedTextColor == currentShape.defaultTextColor) {
                                    selectedTextColor = newShape.defaultTextColor
                                }
                                selectedShape = newShape
                            }
                        )
                    }

                    EmojiPicker(
                        selectedEmoji = selectedEmoji
                        , mapEmojis = mapEmojis
                        , emojiTones = emojiTones
                        , onToneChange = onToneChange
                        , onSelect = {
                            selectedEmoji = it
                        }
                    )

                    val descriptionStyle = LocalTextStyle
                        .current
                        .copy(
                            textAlign = TextAlign.Center
                        )
                    ShowLeadingEmoji(
                        state = richTextState
                        , emoji = selectedEmoji
                        , textStyle = descriptionStyle
                    )
                    RichTextToolbar(richTextState)

                    OutlinedRichTextEditor(
                        state = richTextState
                        , textStyle = descriptionStyle
                        , modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 120.dp
                                , max = 200.dp
                            )
                            .focusRequester(descriptionFocusRequester)
                            .onFocusChanged { isDescriptionFocused = it.hasFocus }
                    )

                    // The colors that follow the map's theme come first, to go back to.
                    ColorPicker(
                        label = "Fill"
                        , colors = remember(color, nodeColors, fillSwatch?.default, fillSwatch?.themed) {
                            (listOfNotNull(fillSwatch?.shown(fillSwatch.default)) + nodeColors
                                + (fillSwatch?.shown(color) ?: color)).distinct()
                        }
                        , selectedColor = shownColor
                        , onSelect = { selectedColor = fillSwatch?.stored(it) ?: it }
                    )

                    ColorPicker(
                        label = "Text"
                        , colors = remember(textColor, textColors, textSwatch?.default, textSwatch?.themed) {
                            (listOfNotNull(textSwatch?.shown(textSwatch.default)) + textColors
                                + (textSwatch?.shown(textColor) ?: textColor)).distinct()
                        }
                        , selectedColor = shownTextColor
                        , onSelect = {
                            selectedTextColor = textSwatch?.stored(it) ?: it
                            // A border that is not on yet is to start in the color of the text it is turned on with.
                            if (selectedBorder.isNone) selectedBorder = selectedBorder.copy(color = it)
                        }
                    )

                    Text(
                        "The first color of each row follows the map's theme."
                        , style = MaterialTheme.typography.labelSmall
                    )

                    CollapsibleSection(
                        title = "Border"
                        , isOpen = isBorderOpen
                        , onToggle = { isBorderOpen = !isBorderOpen }
                    ) {
                        BorderSettings(
                            border = selectedBorder
                            , colors = remember(nodeColors, textColors, selectedTextColor) {
                                (DrawInfo().rootLineColors + nodeColors + textColors + border.color + selectedTextColor)
                                    .distinct()
                            }
                            , onChange = { selectedBorder = it }
                            , modifier = Modifier.padding(12.dp)
                        )
                    }

                    CollapsibleSection(
                        title = "Line"
                        , isOpen = isLineOpen
                        , onToggle = { isLineOpen = !isLineOpen }
                    ) {
                        LineSettings(
                            line = selectedLine
                            , colors = remember(line, nodeColors, textColors) {
                                (DrawInfo().rootLineColors + nodeColors + textColors + listOfNotNull(line.color))
                                    .distinct()
                            }
                            , onChange = { selectedLine = it }
                            , modifier = Modifier.padding(12.dp)
                        )
                    }

                    // A closed note takes no more room than its title, which is the last of the settings then.
                    if (!isNoteOpen) {
                        CollapsibleSection(
                            title = "Note"
                            , isOpen = false
                            , onToggle = { isNoteOpen = true }
                        ) {}
                    }
                }
                , note = if (!isNoteOpen) null else { modifier ->
                    CollapsibleSection(
                        title = "Note"
                        , isOpen = true
                        , onToggle = {
                            // The field is gone once closed, and does not tell that it lost the focus.
                            isNoteFocused = false
                            isNoteOpen = false
                        }
                        , modifier = modifier
                            .onSizeChanged { noteSectionHeight = with(density) { it.height.toDp() } }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                            , verticalAlignment = Alignment.CenterVertically
                            , modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)
                        ) {
                            if (noteMarkdown == null) noteState?.let { RichTextToolbar(it) }
                            RichTextToggle(
                                label = "MD"
                                , labelStyle = SpanStyle(fontFamily = FontFamily.Monospace)
                                , checked = noteMarkdown != null
                                , onToggle = {
                                    val markdown = noteMarkdown
                                    if (markdown == null) {
                                        noteMarkdown = noteToMarkdown(noteState?.let { noteHtml(it) } ?: note)
                                    } else {
                                        scope.launch {
                                            noteState = noteWithImages(noteFromMarkdown(markdown))
                                            noteMarkdown = null
                                        }
                                    }
                                }
                            )
                        }
                        val markdown = noteMarkdown
                        val currentNoteState = noteState
                        ShowEmojisInColor(currentNoteState)
                        val noteModifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(12.dp)
                            .onSizeChanged { noteFieldHeight = with(density) { it.height.toDp() } }
                            .onFocusChanged { isNoteFocused = it.hasFocus }
                        if (markdown == null && currentNoteState == null) {
                            Box(modifier = noteModifier)
                        } else if (markdown == null && currentNoteState != null) {
                            CompositionLocalProvider(LocalImageLoader provides UrlImageLoader) {
                                OutlinedRichTextEditor(
                                    state = currentNoteState
                                    , textStyle = LocalTextStyle.current.copy(color = shownTextColor)
                                    , colors = RichTextEditorDefaults.outlinedRichTextEditorColors(
                                        textColor = shownTextColor
                                        , containerColor = shownColor
                                        , cursorColor = shownTextColor
                                    )
                                    , modifier = noteModifier
                                )
                            }
                        } else if (markdown != null) {
                            OutlinedTextField(
                                value = markdown
                                , onValueChange = { noteMarkdown = it }
                                , visualTransformation = emojiVisualTransformation()
                                , textStyle = LocalTextStyle
                                    .current
                                    .copy(
                                        fontFamily = FontFamily.Monospace
                                        , color = shownTextColor
                                    )
                                , colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = shownTextColor
                                    , unfocusedTextColor = shownTextColor
                                    , focusedContainerColor = shownColor
                                    , unfocusedContainerColor = shownColor
                                    , cursorColor = shownTextColor
                                )
                                , modifier = noteModifier
                            )
                        }
                    }
                }
                , belowNote = {
                    CollapsibleSection(
                        title = "Attributes"
                        , isOpen = isAttributesOpen
                        , onToggle = {
                            // The fields are gone once closed, and do not tell that they lost the focus.
                            if (isAttributesOpen) isAttributesFocused = false
                            isAttributesOpen = !isAttributesOpen
                        }
                    ) {
                        AttributesEditor(
                            rows = attributeRows
                            , knownNames = knownAttributeNames
                            , isFocusedOnStart = startsInAttributes
                            , modifier = Modifier
                                .padding(12.dp)
                                .onFocusChanged { isAttributesFocused = it.hasFocus }
                        )
                    }
                }
                , buttons = {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    onAddAnother?.let { addAnother ->
                        Button(
                            onClick = {
                                save()
                                addAnother()
                            }
                        ) {
                            Text("Add Another")
                        }
                    }
                    Button(onClick = ::submit) {
                        Text("Save & Close")
                    }
                }
            )
        }
    }
}
