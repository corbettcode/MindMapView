package com.mindsync.library.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiFontTest {
    private fun emojiStyle(size: androidx.compose.ui.unit.TextUnit) = SpanStyle(
        fontFamily = FontFamily.Monospace
        , fontSize = size
        , baselineShift = BaselineShift(EMOJI_RAISE)
    )

    @Test
    fun emojiRunsGetTheFont() {
        val styled = AnnotatedString("a 👍🏽 b").withEmojiFont(FontFamily.Monospace)
        assertEquals(listOf(AnnotatedString.Range(emojiStyle(EMOJI_SCALE.em), 2, 6)), styled.spanStyles)
    }

    // The emoji font draws its emojis taller than letters, so they are made smaller to match the text around them.
    @Test
    fun emojisInLargerTextStayInProportion() {
        val text = buildAnnotatedString {
            append("a ")
            pushStyle(SpanStyle(fontSize = 2.em))
            append("👍 b")
            pop()
            pushStyle(SpanStyle(fontSize = 20.sp))
            append(" 🌱")
            pop()
        }
        val emojiSpans = text.withEmojiFont(FontFamily.Monospace).spanStyles.filter { it.item.fontFamily != null }
        assertEquals(
            listOf(
                AnnotatedString.Range(emojiStyle((2 * EMOJI_SCALE).em), 2, 4)
                , AnnotatedString.Range(emojiStyle((20 * EMOJI_SCALE).sp), 7, 9)
            )
            , emojiSpans
        )
    }

    @Test
    fun noFontLeavesTextAlone() =
        assertEquals(AnnotatedString("a 👍"), AnnotatedString("a 👍").withEmojiFont(null))
}
