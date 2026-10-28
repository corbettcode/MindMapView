package com.mohamedrejeb.richeditor.model

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisplayStyleTest {
    private val monospaceLastChar: (AnnotatedString) -> AnnotatedString = { text ->
        buildAnnotatedString {
            append(text)
            if (text.isNotEmpty()) addStyle(SpanStyle(fontFamily = FontFamily.Monospace), text.length - 1, text.length)
        }
    }

    private fun RichTextState.shown(): AnnotatedString = visualTransformation.filter(annotatedString).text

    private fun AnnotatedString.hasMonospaceAt(index: Int): Boolean = spanStyles.any {
        it.item.fontFamily == FontFamily.Monospace && it.start == index && it.end == index + 1
    }

    @Test
    fun displayStyleRestylesTheShownText() {
        val state = RichTextState().setHtml("<p>abc</p>")
        state.displayStyle = monospaceLastChar
        assertEquals("abc", state.shown().text)
        assertTrue(state.shown().hasMonospaceAt(2))
    }

    @Test
    fun displayStyleKeepsApplyingAfterTheTextChanges() {
        val state = RichTextState()
        state.displayStyle = monospaceLastChar
        state.setHtml("<p>abcd</p>")
        assertTrue(state.shown().hasMonospaceAt(3))
    }

    @Test
    fun displayStyleIsNotSaved() {
        val state = RichTextState().setHtml("<p>abc</p>")
        val html = state.toHtml()
        state.displayStyle = monospaceLastChar
        assertEquals(html, state.toHtml())
    }

    // Only the look may change: a style that changes the characters would move the cursor, so it is ignored.
    @Test
    fun displayStyleThatChangesTheTextIsIgnored() {
        val state = RichTextState().setHtml("<p>abc</p>")
        state.displayStyle = { AnnotatedString(it.text + "!") }
        assertEquals("abc", state.shown().text)
    }
}
