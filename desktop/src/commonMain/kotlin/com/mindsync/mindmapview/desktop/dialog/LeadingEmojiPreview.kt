package com.mindsync.mindmapview.desktop.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.mindsync.library.util.EMOJI_RAISE
import com.mindsync.library.util.EMOJI_SCALE
import com.mindsync.library.util.RichTextSize
import com.mindsync.library.util.withEmojiFont
import com.mindsync.mindmapview.desktop.LocalEmojiFontFamily
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Shows [emoji] in front of the text in the editor of [state], at the size it is drawn at in front of the node's text
 * on the map, as it is kept out of the editor. The emoji is drawn in room left at the start of the first line, so
 * the text itself is not changed. Emojis typed into the text are shown in the color emoji font.
 */
@Composable
fun ShowLeadingEmoji(
    state: RichTextState
    , emoji: String?
    , textStyle: TextStyle
) {
    val emojiFontFamily = LocalEmojiFontFamily.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Text without a size of its own is drawn at Compose's default size.
    val sizedStyle = textStyle.copy(fontSize = textStyle.fontSize.takeOrElse { DEFAULT_FONT_SIZE })
    LaunchedEffect(state, emoji, emojiFontFamily, sizedStyle, density) {
        val preview = emoji?.let { LeadingEmoji(it, emojiFontFamily, sizedStyle, textMeasurer, density) }
        state.displayStyle = { text ->
            val shown = emojiFontFamily?.let { text.withEmojiFont(it) } ?: text
            preview?.let { shown.withFirstLineIndent(it.room(text)) } ?: shown
        }
        state.displayDecoration = preview?.let { { layout -> it.draw(this, layout) } }
    }
}

private val DEFAULT_FONT_SIZE = 14.sp

private class LeadingEmoji(
    private val emoji: String
    , private val emojiFontFamily: FontFamily?
    , private val textStyle: TextStyle
    , private val textMeasurer: TextMeasurer
    , private val density: Density
) {
    private fun emojiLayout(scale: Float): TextLayoutResult = textMeasurer.measure(
        emoji
        , textStyle.copy(
            fontFamily = emojiFontFamily ?: textStyle.fontFamily
            , fontSize = textStyle.fontSize * scale * EMOJI_SCALE
            , textAlign = TextAlign.Start
        )
    )

    // The space between the emoji and the text, as wide as a space in the text.
    private fun gap(scale: Float): Float =
        textMeasurer.measure(" ", textStyle.copy(fontSize = textStyle.fontSize * scale)).size.width.toFloat()

    /** The room the emoji takes in front of [text]. */
    fun room(text: AnnotatedString): TextUnit {
        val scale = text.leadingTextScale()
        return with(density) { (emojiLayout(scale).size.width + gap(scale)).toSp() }
    }

    /** Draws the emoji in the room in front of the first line of [layout]. */
    fun draw(scope: DrawScope, layout: TextLayoutResult) {
        if (layout.lineCount == 0) return
        val scale = layout.layoutInput.text.leadingTextScale()
        val emojiLayout = emojiLayout(scale)
        val raise = with(density) { (textStyle.fontSize * scale * EMOJI_SCALE).toPx() } * EMOJI_RAISE
        val left = layout.getLineLeft(0) - emojiLayout.size.width - gap(scale)
        val top = layout.getLineBaseline(0) - emojiLayout.firstBaseline - raise
        scope.drawText(emojiLayout, topLeft = Offset(left, top))
    }
}

/** The size of the first text, against the normal size: that of the text a node's emoji is put in front of. */
internal fun AnnotatedString.leadingTextScale(): Float {
    val start = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return 1f
    return spanStyles
        .lastOrNull { start in it.start until it.end && it.item.fontSize.isSpecified }
        ?.let { RichTextSize.scaleOf(it.item.fontSize) }
        ?: 1f
}

/** This text with room of [indent] at the start of its first line. */
internal fun AnnotatedString.withFirstLineIndent(
    indent: TextUnit
): AnnotatedString {
    // A paragraph style may only lie over whole paragraphs, so it goes over the first one.
    val end = paragraphStyles.firstOrNull { it.start == 0 }?.end
        ?: (text.indexOf('\n').takeIf { it >= 0 }?.plus(1) ?: text.length)
    return buildAnnotatedString {
        append(this@withFirstLineIndent)
        addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent)), 0, end)
    }
}
