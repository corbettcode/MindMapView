package com.mindsync.library.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import com.mindsync.library.emoji.EmojiCatalog

// The color emoji font draws an emoji about as tall as the whole line, a little below the baseline, where a
// capital letter is about 0.7 of the font size. Emojis are drawn smaller and raised, to stand as tall as the
// letters around them.
internal const val EMOJI_SCALE = 0.75f
internal const val EMOJI_RAISE = 0.12f

/**
 * This text with every emoji in it drawn in [fontFamily], as tall as the letters around it, or this text itself
 * when there is no font.
 */
fun AnnotatedString.withEmojiFont(
    fontFamily: FontFamily?
): AnnotatedString {
    if (fontFamily == null) return this
    return buildAnnotatedString {
        append(this@withEmojiFont)
        EmojiCatalog.findEmojis(text).forEach { range ->
            val style = SpanStyle(
                fontFamily = fontFamily
                , fontSize = emojiTextSize(range) * EMOJI_SCALE
                , baselineShift = BaselineShift(EMOJI_RAISE)
            )
            addStyle(style, range.first, range.last + 1)
        }
    }
}

// The size of the text an emoji is in. The emoji picked for a node is put in front of its text, outside the size
// the text was given, so an emoji at the start without a size of its own takes that of the text after it.
private fun AnnotatedString.emojiTextSize(
    range: IntRange
): TextUnit {
    fontSizeAt(range.first)?.let { return it }
    if (range.first == 0) {
        val textStart = (range.last + 1 until text.length).firstOrNull { !text[it].isWhitespace() }
        textStart?.let { fontSizeAt(it) }?.let { return it }
    }
    return 1.em
}

// A span's font size replaces the one around it rather than scaling it, so a size is that of the last span over
// [index] that sets one, or null for the text's own size.
private fun AnnotatedString.fontSizeAt(
    index: Int
): TextUnit? = spanStyles
    .lastOrNull { index in it.start until it.end && it.item.fontSize.isSpecified }
    ?.item
    ?.fontSize
