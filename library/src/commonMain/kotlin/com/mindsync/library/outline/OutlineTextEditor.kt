package com.mindsync.library.outline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mindsync.library.MindMapState
import com.mindsync.library.util.RichText
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/** The test tag of the text field a topic's text is typed in, in its row of the outline. */
const val OUTLINE_TEXT_EDITOR_TAG = "com.mindsync.library.outlineTextEditor"

/** What a key does while a topic's text is typed in its row of the outline, as in an outliner. */
internal enum class OutlineEditorAction {
    /** Ends the typing and adds a sibling below to type in, or only ends it when there is nothing to keep. */
    NEXT_TOPIC
    , INDENT
    , OUTDENT
    , MOVE_UP
    , MOVE_DOWN
    , CANCEL

    /** Removes the topic when its text is empty, and types on in the row above; otherwise deletes as it would. */
    , REMOVE_IF_EMPTY
    , BOLD
    , ITALIC
    , UNDERLINE
}

/** Returns what a key pressed down does while typing in a row, or null when the text field takes it. */
internal fun outlineEditorAction(
    key: Key
    , isShift: Boolean
    , isCtrl: Boolean
    , isAlt: Boolean
): OutlineEditorAction? {
    val isEnter = key == Key.Enter || key == Key.NumPadEnter
    val isPlain = !isCtrl && !isAlt
    return when {
        // Shift+Enter starts a new line in the text, as on the map.
        isEnter && isPlain && !isShift -> OutlineEditorAction.NEXT_TOPIC
        key == Key.Tab && isPlain -> if (isShift) OutlineEditorAction.OUTDENT else OutlineEditorAction.INDENT
        // A Tab with other keys would be drawn as nothing, or take the focus away.
        key == Key.Tab -> null
        isAlt && isShift && !isCtrl && key == Key.DirectionUp -> OutlineEditorAction.MOVE_UP
        isAlt && isShift && !isCtrl && key == Key.DirectionDown -> OutlineEditorAction.MOVE_DOWN
        key == Key.Escape -> OutlineEditorAction.CANCEL
        key == Key.Backspace && isPlain && !isShift -> OutlineEditorAction.REMOVE_IF_EMPTY
        isCtrl && !isAlt && key == Key.B -> OutlineEditorAction.BOLD
        isCtrl && !isAlt && key == Key.I -> OutlineEditorAction.ITALIC
        isCtrl && !isAlt && key == Key.U -> OutlineEditorAction.UNDERLINE
        else -> null
    }
}

/**
 * The text field the text of [MindMapState.textEditNodeId] is typed in, in place of the text of its row, in the
 * row's [textStyle]. Each change shows on the map at once with [MindMapState.previewText]. The keys are those of an
 * outliner, by [outlineEditorAction]; [onNextTopic] adds the sibling Enter goes on to.
 */
@Composable
internal fun OutlineTextEditor(
    state: MindMapState
    , id: String
    , html: String
    , textStyle: TextStyle
    , onNextTopic: () -> Unit
    , modifier: Modifier = Modifier
) {
    key(id) {
        val richTextState = remember { RichTextState().setHtml(html) }
        val focusRequester = remember { FocusRequester() }
        // A topic added for the typing starts with a text that typing replaces; Enter on it as it is adds nothing.
        val startHtml = remember { richTextState.toHtml() }
        LaunchedEffect(Unit) {
            val length = richTextState.annotatedString.text.length
            richTextState.selection =
                if (state.isTextEditStartedByTyping) TextRange(length) else TextRange(0, length)
            focusRequester.requestFocus()
            snapshotFlow { richTextState.annotatedString }
                .map { richTextState.toHtml() }
                .distinctUntilChanged()
                .drop(1)
                .collect { state.previewText(it) }
        }
        BasicRichTextEditor(
            state = richTextState
            , textStyle = textStyle
            , cursorBrush = SolidColor(textStyle.color)
            , modifier = modifier
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    onKey(event, state, richTextState, startHtml, onNextTopic)
                }
                .testTag(OUTLINE_TEXT_EDITOR_TAG)
        )
    }
}

private fun onKey(
    event: KeyEvent
    , state: MindMapState
    , richTextState: RichTextState
    , startHtml: String
    , onNextTopic: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val action = outlineEditorAction(
        event.key
        , event.isShiftPressed
        , event.isCtrlPressed || event.isMetaPressed
        , event.isAltPressed
    ) ?: return false
    val html = richTextState.toHtml()
    when (action) {
        OutlineEditorAction.NEXT_TOPIC -> {
            val isUntouchedNewTopic = state.isTextEditOfAddedNode && html == startHtml
            if (isUntouchedNewTopic || RichText.isBlank(html)) {
                state.cancelTextEdit()
            }
            else {
                state.commitTextEdit()
                onNextTopic()
            }
        }
        OutlineEditorAction.INDENT -> state.indent()
        OutlineEditorAction.OUTDENT -> state.outdent()
        OutlineEditorAction.MOVE_UP -> state.moveAmongSiblings(isUp = true)
        OutlineEditorAction.MOVE_DOWN -> state.moveAmongSiblings(isUp = false)
        OutlineEditorAction.CANCEL -> state.cancelTextEdit()
        OutlineEditorAction.REMOVE_IF_EMPTY -> {
            if (!RichText.isBlank(html)) return false
            state.removeEmptyAndEditAbove()
        }
        OutlineEditorAction.BOLD -> richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        OutlineEditorAction.ITALIC -> richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        OutlineEditorAction.UNDERLINE ->
            richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
    }
    return true
}
