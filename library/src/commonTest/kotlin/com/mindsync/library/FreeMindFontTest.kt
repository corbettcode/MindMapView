package com.mindsync.library

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.mindsync.library.io.FreeMind
import com.mindsync.library.util.RichText
import com.mindsync.library.util.RichTextSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreeMindFontTest {
    // The description of a node of a FreeMind map whose element holds [content], as it is read.
    private fun readDescription(
        content: String
        , text: String = "Granite"
    ): String {
        val map = """
            <map version="1.0.1">
            <node ID="root" TEXT="Rocks">
            <node ID="node" TEXT="$text" POSITION="right">
            $content
            </node>
            </node>
            </map>
        """.trimIndent()
        return FreeMind.read(map).getNode("node").description
    }

    // Whether every character of the text has a style that [predicate] holds for, which may come in several spans.
    private fun AnnotatedString.isAll(predicate: (SpanStyle) -> Boolean): Boolean =
        text.indices.all { index -> spanStyles.any { index in it.start until it.end && predicate(it.item) } }

    private fun AnnotatedString.size(): RichTextSize =
        RichTextSize.of(
            spanStyles.lastOrNull { it.start == 0 && it.end == text.length && it.item.fontSize != TextUnit.Unspecified }
                ?.item?.fontSize ?: TextUnit.Unspecified
        )

    @Test
    fun boldFontMakesTheTextBold() {
        val text = RichText.parse(readDescription("<font NAME=\"SansSerif\" SIZE=\"12\" BOLD=\"true\"/>"))

        assertEquals("Granite", text.text)
        assertTrue(text.isAll { it.fontWeight == FontWeight.Bold })
        assertFalse(text.isAll { it.fontStyle == FontStyle.Italic })
    }

    @Test
    fun italicFontMakesTheTextItalic() {
        val text = RichText.parse(readDescription("<font SIZE=\"12\" ITALIC=\"true\"/>"))

        assertTrue(text.isAll { it.fontStyle == FontStyle.Italic })
        assertFalse(text.isAll { it.fontWeight == FontWeight.Bold })
    }

    @Test
    fun fontCanBeBoldAndItalic() {
        val text = RichText.parse(readDescription("<font SIZE=\"12\" BOLD=\"true\" ITALIC=\"true\"/>"))

        assertTrue(text.isAll { it.fontWeight == FontWeight.Bold })
        assertTrue(text.isAll { it.fontStyle == FontStyle.Italic })
    }

    @Test
    fun fontSizeIsTheNearestSizeOfText() {
        fun size(points: Int) = RichText.parse(readDescription("<font SIZE=\"$points\"/>")).size()

        assertEquals(RichTextSize.SMALL, size(8))
        assertEquals(RichTextSize.SMALL, size(10))
        assertEquals(RichTextSize.NORMAL, size(11))
        assertEquals(RichTextSize.NORMAL, size(14))
        assertEquals(RichTextSize.LARGE, size(15))
        assertEquals(RichTextSize.LARGE, size(24))
    }

    @Test
    fun fontThatChangesNothingLeavesTheDescriptionAsItIs() {
        assertEquals("Granite", readDescription("<font NAME=\"SansSerif\" SIZE=\"12\"/>"))
        assertEquals("Granite", readDescription("<font NAME=\"Serif\" BOLD=\"false\" ITALIC=\"false\"/>"))
    }

    @Test
    fun fontIsAppliedToRichContentToo() {
        val description = readDescription(
            """
            <font SIZE="12" ITALIC="true"/>
            <richcontent TYPE="NODE"><html><head></head><body><p>rich <b>text</b></p></body></html></richcontent>
            """.trimIndent(),
        )

        val text = RichText.parse(description)
        assertEquals("rich text", text.text)
        assertTrue(text.isAll { it.fontStyle == FontStyle.Italic })
        val bold = text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(listOf("text"), bold.map { text.text.substring(it.start, it.end) })
    }

    @Test
    fun fontIsKeptWhenTheMapIsSavedAndOpenedAgain() {
        val map = """
            <map version="1.0.1">
            <node ID="root" TEXT="Rocks">
            <node ID="node" TEXT="Granite" POSITION="right"><font SIZE="18" BOLD="true"/></node>
            </node>
            </map>
        """.trimIndent()

        val reopened = FreeMind.read(FreeMind.write(FreeMind.read(map)))

        val text = RichText.parse(reopened.getNode("node").description)
        assertEquals("Granite", text.text)
        assertTrue(text.isAll { it.fontWeight == FontWeight.Bold })
        assertEquals(RichTextSize.LARGE, text.size())
    }
}
