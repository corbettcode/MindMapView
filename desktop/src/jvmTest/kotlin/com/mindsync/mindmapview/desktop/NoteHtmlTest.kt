package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.TextRange
import com.mindsync.mindmapview.desktop.component.HIGHLIGHT
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteHtmlTest {
    @Test
    fun insertedImageIsInTheNote() {
        val state = RichTextState().setHtml("<p>before</p>")
        state.selection = TextRange(state.annotatedString.text.length)

        state.insertHtmlAfterSelection("<img src=\"ccmm-image:abc\">")

        val html = noteHtml(state)
        assertTrue("before" in html, html)
        assertTrue("<img src=\"ccmm-image:abc\"" in html, html)
    }

    @Test
    fun emptiedEditorIsNoNote() {
        assertEquals("", noteHtml(RichTextState().setHtml("<p></p>")))
        assertEquals("", noteFromSource("  "))
    }

    @Test
    fun markupTypedInTheEditorStaysText() {
        assertEquals("<p>pressure &lt; 5 bar</p>", noteHtml(RichTextState().setHtml("<p>pressure &lt; 5 bar</p>")))
    }

    @Test
    fun markupTypedAsSourceTakesEffect() {
        assertEquals("<p>a <b>bold</b> word</p>", noteFromSource("<p>a <b>bold</b> word</p>"))
    }

    @Test
    fun imageWithoutASizeIsKeptWithoutOne() {
        assertEquals(
            "<p><img src=\"https://e.org/x.png\"></p>",
            noteFromSource("<img src=\"https://e.org/x.png\"/>"),
        )
    }

    @Test
    fun imageWithASizeKeepsIt() {
        assertEquals(
            "<p><img src=\"https://e.org/x.png\" width=\"100\" height=\"50\"></p>",
            noteFromSource("<p><img src=\"https://e.org/x.png\" width=\"100\" height=\"50\"></p>"),
        )
        // A size is read again as it was written.
        val note = noteFromSource("<p><img src=\"https://e.org/x.png\" width=\"100\" height=\"50\"></p>")
        assertEquals(note, noteFromSource(note))
    }

    @Test
    fun highlightIsTurnedOffAndOn() {
        val state = RichTextState().setHtml("<p><mark>CQRS</mark></p>")
        state.selection = TextRange(0, state.annotatedString.text.length)

        state.toggleSpanStyle(HIGHLIGHT)
        assertEquals("<p>CQRS</p>", noteHtml(state))

        state.toggleSpanStyle(HIGHLIGHT)
        assertEquals("<p><mark>CQRS</mark></p>", noteHtml(state))
    }
}
