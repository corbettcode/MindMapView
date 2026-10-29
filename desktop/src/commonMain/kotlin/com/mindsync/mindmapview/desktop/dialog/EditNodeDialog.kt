package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeShape
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.mindmapview.desktop.AppDialog
import com.mindsync.mindmapview.desktop.AppDialogState
import com.mindsync.mindmapview.desktop.EDIT_DIALOG_MIN_WIDTH
import com.mindsync.mindmapview.desktop.EDIT_DIALOG_SIZE
import com.mindsync.mindmapview.desktop.NOTE_LINES
import com.mindsync.mindmapview.desktop.ShowEmojisInColor
import com.mindsync.mindmapview.desktop.TEXT_FIELD_VERTICAL_PADDING
import com.mindsync.mindmapview.desktop.UrlImageLoader
import com.mindsync.mindmapview.desktop.component.ColorPicker
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
 * @param nodeColors A list of colors available for selecting the node's fill color.
 * @param textColors A list of colors available for selecting the node's text color.
 * @param mapEmojis A list of emoji strings available for selection and customization within the dialog.
 * @param emojiTones A map that associates emojis to their tonal variations.
 * @param onToneChange A callback triggered when the tone of an emoji is changed. Receives the base emoji
 * and its selected tonal variant.
 * @param onSubmit A callback triggered upon confirming the dialog. Receives the updated description,
 * note, shape, fill color, text color, and line settings of the node.
 * @param onDismiss A callback triggered when the dialog is dismissed.
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
    , nodeColors: List<Color>
    , textColors: List<Color>
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
    ) -> Unit
    , onDismiss: () -> Unit
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
    var selectedLine by remember { mutableStateOf(line) }
    // Closed unless the node has a line setting of its own, so the dialog fits on a small screen.
    var isLineOpen by remember { mutableStateOf(!line.isEmpty) }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineStyle = LocalTextStyle.current
    val noteMinHeight = remember(density, lineStyle) {
        val lines = textMeasurer.measure("\n".repeat(NOTE_LINES - 1), lineStyle).size.height
        with(density) { lines.toDp() } + TEXT_FIELD_VERTICAL_PADDING
    }
    // Its least width; its height is fitted to what its content needs.
    val dialogState = remember { AppDialogState(EDIT_DIALOG_SIZE, minSize = DpSize(EDIT_DIALOG_MIN_WIDTH, 0.dp)) }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var isNoteFocused by remember { mutableStateOf(false) }

    fun submit() {
        onSubmit(
            DescriptionEmoji.prepend(selectedEmoji, richTextState.toHtml())
            , noteMarkdown?.let { noteFromMarkdown(it) } ?: noteState?.let { noteHtml(it) } ?: note
            , selectedShape
            , selectedColor
            , selectedTextColor
            , selectedLine
        )
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
            descriptionFocusRequester.requestFocus()
        }
        // Enter clicks OK, wherever the focus is but in the note, whose lines are typed with Enter. A line of the
        // node's text is typed with Alt+Enter instead.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    when {
                        event.key != Key.Enter && event.key != Key.NumPadEnter -> false
                        isNoteFocused -> false
                        event.type != KeyEventType.KeyDown -> true
                        event.isAltPressed -> {
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

                    ColorPicker(
                        label = "Fill"
                        , colors = remember(color, nodeColors) { (nodeColors + color).distinct() }
                        , selectedColor = selectedColor
                        , onSelect = { selectedColor = it }
                    )

                    ColorPicker(
                        label = "Text"
                        , colors = remember(textColor, textColors) { (textColors + textColor).distinct() }
                        , selectedColor = selectedTextColor
                        , onSelect = { selectedTextColor = it }
                    )

                    Text(
                        if (isLineOpen) "Line ▾" else "Line ▸"
                        , style = MaterialTheme.typography.titleSmall
                        , modifier = Modifier.clickable {
                            isLineOpen = !isLineOpen
                        }
                    )

                    if (isLineOpen) {
                        LineSettings(
                            line = selectedLine
                            , colors = remember(line, nodeColors, textColors) {
                                (DrawInfo().rootLineColors + nodeColors + textColors + listOfNotNull(line.color))
                                    .distinct()
                            }
                            , onChange = { selectedLine = it }
                        )
                    }

                    Text(
                        "Note"
                        , style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                        , verticalAlignment = Alignment.CenterVertically
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
                }
                , note = { modifier ->
                    val markdown = noteMarkdown
                    val currentNoteState = noteState
                    ShowEmojisInColor(currentNoteState)
                    val noteModifier = modifier
                        .onFocusChanged { isNoteFocused = it.hasFocus }
                    if (markdown == null && currentNoteState == null) {
                        Box(modifier = noteModifier)
                    } else if (markdown == null && currentNoteState != null) {
                        CompositionLocalProvider(LocalImageLoader provides UrlImageLoader) {
                            OutlinedRichTextEditor(
                                state = currentNoteState
                                , textStyle = LocalTextStyle.current.copy(color = selectedTextColor)
                                , colors = RichTextEditorDefaults.outlinedRichTextEditorColors(
                                    textColor = selectedTextColor
                                    , containerColor = selectedColor
                                    , cursorColor = selectedTextColor
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
                                    , color = selectedTextColor
                                )
                            , colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = selectedTextColor
                                , unfocusedTextColor = selectedTextColor
                                , focusedContainerColor = selectedColor
                                , unfocusedContainerColor = selectedColor
                                , cursorColor = selectedTextColor
                            )
                            , modifier = noteModifier
                        )
                    }
                }
                , buttons = {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = ::submit) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
