package com.mindsync.mindmapview.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteMarkdownTest {
    // A note is the same once it has been shown as Markdown and read back.
    private fun assertRoundTrip(html: String) {
        val markdown = noteToMarkdown(html)
        assertEquals(noteFromSource(html), noteFromMarkdown(markdown), "through Markdown:\n$markdown")
    }

    @Test
    fun listsAfterAParagraphKeepTheirText() {
        assertRoundTrip("<p>before</p><ul><li>one</li><li>two</li></ul><ol><li>first</li><li>second</li></ol>")
    }

    @Test
    fun charactersMarkdownUsesStayText() {
        assertRoundTrip("<p>a &lt; b * c _d_ [e] # f</p>")
    }

    @Test
    fun formattingAndLinksAreKept() {
        assertRoundTrip("<p><b>bold</b> <i>italic</i> <s>gone</s> <a href=\"https://x.org\">link</a></p>")
    }

    @Test
    fun formattingMarkdownHasNoWayToWriteIsKept() {
        assertRoundTrip("<p><u>under</u> and <span style=\"font-size: 1.5em\">big</span></p>")
    }

    @Test
    fun imagesAreKept() {
        assertRoundTrip("<p>see <img src=\"https://e.org/x.png\"> here</p>")
    }

    @Test
    fun paragraphsAreKept() {
        assertRoundTrip("<p>first</p><p>second</p>")
    }

    @Test
    fun imageIsWrittenAsMarkdown() {
        assertTrue("![](https://e.org/x.png)" in noteToMarkdown("<p><img src=\"https://e.org/x.png\"></p>"))
    }

    @Test
    fun imageNamedAsAnEmojiStaysAnImage() {
        assertTrue("![](https://e.org/cat.png)" in noteToMarkdown("<p><img src=\"https://e.org/cat.png\"></p>"))
    }

    @Test
    fun imageWithASizeKeepsIt() {
        assertRoundTrip("<p><img src=\"https://e.org/x.png\" width=\"100\" height=\"50\"></p>")
    }

    @Test
    fun typedMarkdownTakesEffect() {
        val note = noteFromMarkdown("Some **bold** and ~~strike~~\n\n![Jasper](https://e.org/j.jpg)\n\n- one\n- two")
        assertTrue("<b>bold</b>" in note, note)
        assertTrue("<s>strike</s>" in note, note)
        assertTrue("<img src=\"https://e.org/j.jpg\"" in note, note)
        assertTrue("<ul><li>one</li><li>two</li></ul>" in note, note)
        assertFalse("<br>" in note, note)
    }

    @Test
    fun emptyMarkdownIsNoNote() {
        assertEquals("", noteFromMarkdown(" \n "))
        assertEquals("", noteToMarkdown(""))
    }
}
