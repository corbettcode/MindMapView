package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mindsync.library.util.RichTextSize
import com.mindsync.mindmapview.desktop.dialog.leadingTextScale
import com.mindsync.mindmapview.desktop.dialog.withFirstLineIndent
import kotlin.test.Test
import kotlin.test.assertEquals

class LeadingEmojiPreviewTest {
    @Test
    fun scaleIsThatOfTheFirstText() {
        val text = buildAnnotatedString {
            append("  ")
            pushStyle(RichTextSize.LARGE.spanStyle)
            append("big")
            pop()
            append(" small")
        }
        assertEquals(RichTextSize.LARGE.scale, text.leadingTextScale())
    }

    // The editor reads sizes in a description as CSS pixels.
    @Test
    fun scaleOfAPixelSizeIsAgainstTheNormalSize() {
        val text = buildAnnotatedString {
            pushStyle(SpanStyle(fontSize = 24.sp))
            append("big")
            pop()
        }
        assertEquals(1.5f, text.leadingTextScale())
    }

    @Test
    fun textWithoutASizeHasTheNormalScale() = assertEquals(1f, AnnotatedString("plain").leadingTextScale())

    @Test
    fun onlyTheFirstParagraphMakesRoom() {
        val text = buildAnnotatedString {
            pushStyle(ParagraphStyle(textAlign = TextAlign.Center))
            append("first")
            pop()
            pushStyle(ParagraphStyle(textAlign = TextAlign.Center))
            append("second")
            pop()
        }
        val indented = text.withFirstLineIndent(12.sp).paragraphStyles
            .filter { it.item.textIndent == TextIndent(firstLine = 12.sp) }
            .map { it.start to it.end }
        assertEquals(listOf(0 to 5), indented)
    }

    @Test
    fun textWithoutParagraphsMakesRoomUpToItsFirstLineBreak() {
        val indented = AnnotatedString("one\ntwo").withFirstLineIndent(1.em).paragraphStyles
            .map { it.start to it.end }
        assertEquals(listOf(0 to 4), indented)
    }
}
