package com.mindsync.library

import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.Tree
import com.mindsync.library.io.HtmlOutline
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlOutlineTest {
    // "Rocks" with "Igneous" below it and "Granite" below that, and "Sedimentary" below "Rocks".
    private fun rocks(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val root = getRootNode()
        setRootNode(root.copy(description = "Rocks"))
        addNode("igneous", root.id, "Igneous")
        addNode("granite", "igneous", "Granite")
        addNode("sedimentary", root.id, "Sedimentary")
    }

    private fun String.body(): String = substringAfter("<body>").substringBefore("</body>").trim()

    @Test
    fun outlineIsAPageTitledAfterTheRoot() {
        val html = HtmlOutline.write(rocks())

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue("<meta charset=\"utf-8\">" in html)
        assertTrue("<title>Rocks</title>" in html)
        assertTrue(html.trim().endsWith("</html>"))
    }

    @Test
    fun nodesAreNestedListsAndANodeWithChildrenFolds() {
        val html = HtmlOutline.write(rocks())

        assertEquals(
            """
            <ul>
            <li><details open><summary><span class="node">Rocks</span></summary>
            <ul>
            <li><details open><summary><span class="node">Igneous</span></summary>
            <ul>
            <li><span class="node">Granite</span></li>
            </ul>
            </details></li>
            <li><span class="node">Sedimentary</span></li>
            </ul>
            </details></li>
            </ul>
            """.trimIndent(),
            html.body(),
        )
    }

    @Test
    fun foldedNodeStartsFolded() {
        val tree = rocks()
        tree.setFolded("igneous", true)

        val html = HtmlOutline.write(tree)

        assertTrue("<li><details><summary><span class=\"node\">Igneous</span></summary>" in html)
        // The nodes hidden in it are still written.
        assertTrue("Granite" in html)
    }

    @Test
    fun richTextAndLinksAreKept() {
        val tree = rocks()
        tree.updateNode("granite", "<b>Granite</b> <a href=\"https://example.com\">more</a>", emptyList())

        val html = HtmlOutline.write(tree)

        assertTrue("<span class=\"node\"><b>Granite</b> <a href=\"https://example.com\">more</a></span>" in html)
    }

    @Test
    fun noteAndAttributesAreWrittenBelowTheirNode() {
        val tree = rocks()
        tree.updateNodeNote("granite", "<p>Coarse grained</p>")
        tree.setAttributes("granite", listOf(NodeAttribute("hardness", "6 < 7 & more")))

        val html = HtmlOutline.write(tree)

        val node = html.indexOf("<span class=\"node\">Granite</span>")
        val note = html.indexOf("<div class=\"note\"><p>Coarse grained</p></div>")
        val attributes = html.indexOf(
            "<table class=\"attributes\"><tr><td>hardness</td><td>6 &lt; 7 &amp; more</td></tr></table>"
        )
        assertTrue(node in 0 until note, html)
        assertTrue(note < attributes, html)
    }

    @Test
    fun nodeWithoutANoteHasNoNote() {
        val html = HtmlOutline.write(rocks())

        assertFalse("class=\"note\"" in html)
        assertFalse("class=\"attributes\"" in html)
    }

    @Test
    fun titleIsThePlainTextOfTheRootEscaped() {
        val tree = rocks()
        tree.setRootNode(tree.getRootNode().copy(description = "<i>Rocks</i> &amp; minerals"))

        val html = HtmlOutline.write(tree)

        assertTrue("<title>Rocks &amp; minerals</title>" in html, html)
    }
}
