package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.mindsync.library.data.NodeShape
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.mindmapview.desktop.EDIT_DIALOG_MIN_WIDTH
import com.mindsync.mindmapview.desktop.EDIT_DIALOG_SIZE
import com.mindsync.mindmapview.desktop.NOTE_LINES
import com.mindsync.mindmapview.desktop.TEXT_FIELD_VERTICAL_PADDING
import com.mindsync.mindmapview.desktop.UrlImageLoader
import com.mindsync.mindmapview.desktop.component.ColorPicker
import com.mindsync.mindmapview.desktop.component.EmojiPicker
import com.mindsync.mindmapview.desktop.component.RichTextToggle
import com.mindsync.mindmapview.desktop.component.RichTextToolbar
import com.mindsync.mindmapview.desktop.component.ShapePicker
import com.mindsync.mindmapview.desktop.noteFromMarkdown
import com.mindsync.mindmapview.desktop.noteHtml
import com.mindsync.mindmapview.desktop.noteToMarkdown
import com.mindsync.mindmapview.desktop.noteWithImages
import com.mohamedrejeb.richeditor.model.LocalImageLoader
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import kotlinx.coroutines.launch
import java.awt.Dimension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeDialog(
    title: String,
    description: String,
    isDescriptionSelected: Boolean,
    note: String,
    shape: NodeShape?,
    color: Color,
    textColor: Color,
    nodeColors: List<Color>,
    textColors: List<Color>,
    onSubmit: (description: String, note: String, shape: NodeShape?, color: Color, textColor: Color) -> Unit,
    onDismiss: () -> Unit,
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

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineStyle = LocalTextStyle.current
    val noteMinHeight = remember(density, lineStyle) {
        val lines = textMeasurer.measure("\n".repeat(NOTE_LINES - 1), lineStyle).size.height
        with(density) { lines.toDp() } + TEXT_FIELD_VERTICAL_PADDING
    }
    var noteHeight by remember { mutableStateOf<Dp?>(null) }
    val dialogState = rememberDialogState(size = EDIT_DIALOG_SIZE)
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var isNoteFocused by remember { mutableStateOf(false) }

    fun submit() {
        onSubmit(
            DescriptionEmoji.prepend(selectedEmoji, richTextState.toHtml())
            , noteMarkdown?.let { noteFromMarkdown(it) } ?: noteState?.let { noteHtml(it) } ?: note
            , selectedShape
            , selectedColor
            , selectedTextColor
        )
        onDismiss()
    }

    // A window of its own, so it can be resized: the note takes the room the rest leaves, which is four lines
    // to start with and at the least.
    DialogWindow(
        onCloseRequest = onDismiss
        , state = dialogState
        , title = title
    ) {
        LaunchedEffect(Unit) {
            descriptionFocusRequester.requestFocus()
        }
        // The rest of the dialog is only known once it is laid out, so the window is fitted to it then.
        LaunchedEffect(noteHeight != null) {
            val height = dialogState.size.height - ((noteHeight ?: return@LaunchedEffect) - noteMinHeight)
            dialogState.size = DpSize(dialogState.size.width, height)
            window.minimumSize = Dimension(EDIT_DIALOG_MIN_WIDTH.value.toInt(), height.value.toInt())
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
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                selectedShape?.let { currentShape ->
                    ShapePicker(
                        selectedShape = currentShape,
                        onSelect = { newShape ->
                            // A color that was not chosen is that of the shape, so it goes along with the shape.
                            if (selectedColor == currentShape.defaultColor) selectedColor = newShape.defaultColor
                            if (selectedTextColor == currentShape.defaultTextColor) {
                                selectedTextColor = newShape.defaultTextColor
                            }
                            selectedShape = newShape
                        },
                    )
                }

                EmojiPicker(
                    selectedEmoji = selectedEmoji
                    , onSelect = {
                        selectedEmoji = it
                    }
                )

                RichTextToolbar(richTextState)

                OutlinedRichTextEditor(
                    state = richTextState
                    , textStyle = LocalTextStyle
                        .current
                        .copy(
                            textAlign = TextAlign.Center
                        )
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
                    "Note"
                    , style = MaterialTheme.typography.titleSmall
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (noteMarkdown == null) noteState?.let { RichTextToolbar(it) }
                    RichTextToggle(
                        label = "MD",
                        labelStyle = SpanStyle(fontFamily = FontFamily.Monospace),
                        checked = noteMarkdown != null,
                        onToggle = {
                            val markdown = noteMarkdown
                            if (markdown == null) {
                                noteMarkdown = noteToMarkdown(noteState?.let { noteHtml(it) } ?: note)
                            }
                            else {
                                scope.launch {
                                    noteState = noteWithImages(noteFromMarkdown(markdown))
                                    noteMarkdown = null
                                }
                            }
                        },
                    )
                }
                val markdown = noteMarkdown
                val currentNoteState = noteState
                val noteModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { if (noteHeight == null) noteHeight = with(density) { it.height.toDp() } }
                    .onFocusChanged { isNoteFocused = it.hasFocus }
                if (markdown == null && currentNoteState == null) {
                    Box(modifier = noteModifier)
                }
                else if (markdown == null && currentNoteState != null) {
                    CompositionLocalProvider(LocalImageLoader provides UrlImageLoader) {
                        OutlinedRichTextEditor(state = currentNoteState, modifier = noteModifier)
                    }
                }
                else if (markdown != null) {
                    OutlinedTextField(
                        value = markdown,
                        onValueChange = { noteMarkdown = it },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onFocusChanged { isNoteFocused = it.hasFocus },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = ::submit) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
