package com.mindsync.library.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiFontTest {
    @Test
    fun emojiRunsGetTheFont() {
        val styled = AnnotatedString("a 👍🏽 b").withEmojiFont(FontFamily.Monospace)
        assertEquals(
            listOf(AnnotatedString.Range(SpanStyle(fontFamily = FontFamily.Monospace), 2, 6))
            , styled.spanStyles
        )
    }

    @Test
    fun noFontLeavesTextAlone() =
        assertEquals(AnnotatedString("a 👍"), AnnotatedString("a 👍").withEmojiFont(null))
}
