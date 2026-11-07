package com.mindsync.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.util.toPx
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** The test tag of the text field a node's text is typed in. */
const val NODE_TEXT_EDITOR_TAG = "com.mindsync.library.nodeTextEditor"

/**
 * The text field the text of [MindMapState.textEditNodeId] is typed in, over where the node's text is drawn, in its
 * font, size and color, and moved and scaled with the map. Each change of the text is shown in the node with
 * [MindMapState.previewText], so the node grows around it. Enter ends the typing, Escape cancels it, Shift+Enter
 * starts a new line and Ctrl+B, Ctrl+I and Ctrl+U make the selected text bold, italic or underlined. An app that
 * handles Enter, Tab or Escape itself, before the text field gets them, decides what they do instead.
 */
@Composable
internal fun NodeTextEditor(
    state: MindMapState
    , measureTextSize: MeasureTextSize
) {
    val id = state.textEditNodeId ?: return
    val startNode = state.tree.nodes[id] ?: return
    val density = LocalDensity.current
    key(id) {
        val richTextState = remember { RichTextState().setHtml(startNode.description) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            val length = richTextState.annotatedString.text.length
            richTextState.selection =
                if (state.isTextEditStartedByTyping) TextRange(length) else TextRange(0, length)
            focusRequester.requestFocus()
            // The editor's paragraphs change without telling of it, but the text it shows does tell. The first is
            // the text the node has already, written as the editor writes it.
            snapshotFlow { richTextState.annotatedString }
                .map { richTextState.toHtml() }
                .distinctUntilChanged()
                .drop(1)
                .collect { state.previewText(it) }
        }
        val textColor = state.tree.textColorOf(id)
        BasicRichTextEditor(
            state = richTextState
            , textStyle = measureTextSize.textStyleOf(state.tree.topicKindOf(id)).copy(color = textColor)
            , cursorBrush = SolidColor(textColor)
            , modifier = Modifier
                // Placed as the map is drawn, which it reads while it is laid out, so it follows each frame of a
                // move or zoom without composing again.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints())
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        state.drawTick
                        val node = state.tree.nodes[id] ?: return@layout
                        val content =
                            measureTextSize.measureContent(state.tree, node, state.attributeDisplay, state.noteDisplay)
                        val center = Offset(
                            node.path.centerX.toPx(density) + content.textShiftX
                            , node.path.centerY.toPx(density) - content.textRise
                        ) * state.scaleFactor + state.currentPoint
                        placeable.placeWithLayer(
                            (center.x - placeable.width / 2f).roundToInt()
                            , (center.y - placeable.height / 2f).roundToInt()
                        ) {
                            scaleX = state.scaleFactor
                            scaleY = state.scaleFactor
                            transformOrigin = TransformOrigin.Center
                        }
                    }
                }
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { onEditorKey(it, state, richTextState) }
                .testTag(NODE_TEXT_EDITOR_TAG)
        )
    }
}

private fun onEditorKey(
    event: KeyEvent
    , state: MindMapState
    , richTextState: RichTextState
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val isShortcut = event.isCtrlPressed || event.isMetaPressed
    return when {
        (event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed && !isShortcut -> {
            state.commitTextEdit()
            true
        }
        // A tab in the text would be drawn as nothing, so Tab ends the typing as Enter does.
        event.key == Key.Tab -> {
            state.commitTextEdit()
            true
        }
        event.key == Key.Escape -> {
            state.cancelTextEdit()
            true
        }
        isShortcut && event.key == Key.B -> richTextState.toggle(SpanStyle(fontWeight = FontWeight.Bold))
        isShortcut && event.key == Key.I -> richTextState.toggle(SpanStyle(fontStyle = FontStyle.Italic))
        isShortcut && event.key == Key.U -> richTextState.toggle(SpanStyle(textDecoration = TextDecoration.Underline))
        else -> false
    }
}

private fun RichTextState.toggle(style: SpanStyle): Boolean {
    toggleSpanStyle(style)
    return true
}
