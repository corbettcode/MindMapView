package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiTextFieldTest {
    @Test
    fun typedEmojisAreShownInTheEmojiFont() {
        val shown = EmojiVisualTransformation(FontFamily.Monospace).filter(AnnotatedString("a 👍🏽 b"))
        assertEquals("a 👍🏽 b", shown.text.text)
        assertEquals(
            listOf(AnnotatedString.Range(SpanStyle(fontFamily = FontFamily.Monospace), 2, 6))
            , shown.text.spanStyles
        )
    }

    // The text is only styled, not changed, so a cursor stays where it is.
    @Test
    fun offsetsStayTheSame() {
        val mapping = EmojiVisualTransformation(FontFamily.Monospace).filter(AnnotatedString("a 👍🏽 b")).offsetMapping
        (0..8).forEach {
            assertEquals(it, mapping.originalToTransformed(it))
            assertEquals(it, mapping.transformedToOriginal(it))
        }
    }

    @Test
    fun richTextEditorsShowEmojisInTheEmojiFont() {
        val shown = emojiDisplayStyle(FontFamily.Monospace)(AnnotatedString("a 👍🏽 b"))
        assertEquals("a 👍🏽 b", shown.text)
        assertEquals(
            listOf(AnnotatedString.Range(SpanStyle(fontFamily = FontFamily.Monospace), 2, 6))
            , shown.spanStyles
        )
    }
}
