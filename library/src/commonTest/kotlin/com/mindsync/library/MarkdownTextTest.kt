package com.mindsync.library

import com.mindsync.library.io.Markdown
import com.mindsync.library.io.MarkdownText
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTextTest {
    // What the importer makes of the text written as a heading, as the description of the root.
    private fun roundTrip(html: String): String = Markdown
        .read("# " + MarkdownText.inline(html), "t")
        .getRootNode()
        .description

    private fun plainRoundTrip(text: String): String = RichText.parse(roundTrip(RichText.fromPlainText(text))).text

    @Test
    fun boldItalicStrikeAndLinksAreMarkdown() {
        assertEquals(
            "<b>a</b> <i>b</i> <s>c</s> <a href=\"https://e.org\">d</a>"
            , roundTrip("<b>a</b> <i>b</i> <s>c</s> <a href=\"https://e.org\">d</a>")
        )
    }

    @Test
    fun boldAndItalicTogether() {
        val html = roundTrip("<b><i>x</i></b>")

        assertTrue("<b>" in html && "<i>" in html, html)
        assertEquals("x", RichText.parse(html).text)
    }

    @Test
    fun boldThenBoldItalic() {
        val html = roundTrip("<b>a <i>b</i></b>")

        assertEquals("a b", RichText.parse(html).text)
        assertTrue(html.startsWith("<b>a"), html)
        assertTrue("<i>b</i>" in html, html)
    }

    @Test
    fun styledSpansFromTheEditorAreRead() {
        assertEquals("<b>a</b>", roundTrip("<span style=\"font-weight: bold\">a</span>"))
    }

    @Test
    fun underlineColorAndSizeAreDropped() {
        assertEquals("a", roundTrip("<u>a</u>"))
        assertEquals("a", roundTrip("<span style=\"color: red\">a</span>"))
    }

    @Test
    fun markupCharactersRoundTripAsText() {
        listOf(
            "2*3*4"
            , "snake_case_name"
            , "~~x~~"
            , "[x](y)"
            , "# not a heading"
            , "- not an item"
            , "1. not an item"
            , "a | b"
            , "<https://x>"
            , "Tom & Jerry <3"
            , "**not bold**"
        ).forEach { text ->
            assertEquals(text, plainRoundTrip(text), "for $text")
        }
    }

    @Test
    fun backtickBecomesAnApostrophe() {
        assertEquals("a'b", plainRoundTrip("a`b"))
    }

    @Test
    fun lineBreaksInADescriptionBecomeSpaces() {
        assertEquals("a b", MarkdownText.inline("a<br>b"))
    }

    @Test
    fun noteParagraphsAreItsLines() {
        assertEquals(listOf("one", "two", "three"), MarkdownText.paragraphs("<p>one</p><p>two<br>three</p>"))
    }

    @Test
    fun noteImagesAreMarkdownImages() {
        val paragraphs = MarkdownText.paragraphs("<p>see <img src=\"https://e.org/x.png\" alt=\"cat\"></p>")

        assertTrue(paragraphs.single().contains("![cat](https://e.org/x.png)"), paragraphs.toString())
    }

    @Test
    fun paragraphStartingWithBoldIsNotBoldAtItsStart() {
        assertEquals(listOf("Note: text"), MarkdownText.paragraphs("<b>Note:</b> text"))
    }

    @Test
    fun cellEscapesPipes() {
        assertEquals("a\\|b", MarkdownText.cell("a|b"))
    }
}
