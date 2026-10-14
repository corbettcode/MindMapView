package com.mindsync.mindmapview.desktop

import com.mohamedrejeb.richeditor.model.RichTextState
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteHtmlTest {
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
            "<p><img src=\"https://e.org/x.png\" width=\"100.0\" height=\"50.0\"></p>",
            noteFromSource("<p><img src=\"https://e.org/x.png\" width=\"100\" height=\"50\"></p>"),
        )
    }
}
