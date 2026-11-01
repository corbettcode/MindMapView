package com.mindsync.library.util

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextLinesTest {
    private fun linesOf(html: String): List<String> = RichText.lines(html).map { it.text }

    @Test
    fun paragraphsAndLineBreaksAreLines() {
        assertEquals(listOf("one", "two", "three"), linesOf("<p>one</p><p>two<br>three</p>"))
    }

    @Test
    fun plainTextIsOneLine() {
        assertEquals(listOf("just text"), linesOf("just text"))
    }

    @Test
    fun blankLinesAreDropped() {
        assertEquals(listOf("a", "b"), linesOf("<p>a</p><p></p><p>b</p>"))
    }

    @Test
    fun emptyTextHasNoLines() {
        assertEquals(emptyList(), linesOf(""))
    }

    @Test
    fun styleOverALineBreakIsKeptOnBothLines() {
        val lines = RichText.lines("<b>a<br>b</b>")

        assertEquals(listOf("a", "b"), lines.map { it.text })
        lines.forEach { line ->
            assertTrue(line.spanStyles.any { it.item.fontWeight == FontWeight.Bold }, "line ${line.text} is bold")
        }
    }

    @Test
    fun listItemsAreLines() {
        assertEquals(listOf("x", "y"), linesOf("<ul><li>x</li><li>y</li></ul>"))
    }
}
