package com.mindsync.library.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.mindsync.library.emoji.EmojiCatalog

/**
 * This text with every emoji in it drawn in [fontFamily], or this text itself when there is no font.
 */
fun AnnotatedString.withEmojiFont(
    fontFamily: FontFamily?
): AnnotatedString {
    if (fontFamily == null) return this
    val emojiStyle = SpanStyle(fontFamily = fontFamily)
    return buildAnnotatedString {
        append(this@withEmojiFont)
        EmojiCatalog.findEmojis(text).forEach { range ->
            addStyle(emojiStyle, range.first, range.last + 1)
        }
    }
}
