package com.mindsync.library

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.HtmlOutline
import com.mindsync.library.io.Markdown
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.io.Opml
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryFilesTest {
    // The root with "p" below it, and "a", "b", "c" and "d" below "p".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("p", getRootNode().id, "P")
        listOf("a", "b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
    }

    // Adds the summary [id] of [childIds] right after the last of them, as the app adds one.
    private fun Tree<Any>.summary(
        id: String
        , vararg childIds: String
    ) {
        val parentId = getNode(childIds.first()).parentId!!
        val index = getNode(parentId).children.indexOfLast { it in childIds } + 1
        addNode(id, parentId, id.uppercase(), side = sideOf(childIds.first()), index = index)
        setSummary(id, childIds.toSet())
    }

    private fun Tree<Any>.textOf(id: String): String = RichText.parse(getNode(id).description).text

    private fun Tree<Any>.node(text: String): NodeData<*> = nodes.values.single { textOf(it.id) == text }

    private fun Tree<Any>.childTexts(text: String): List<String> = node(text).children.map { textOf(it) }

    // Each summary of the tree by its text, with the texts of the siblings it is about.
    private fun Tree<Any>.summaryTexts(): Map<String, List<String>> =
        summaries.entries.associate { (id, childIds) -> textOf(id) to childIds.map { textOf(it) } }

    // The order the nodes below "P" are written in a FreeMind map, with "-" for an empty one.
    private fun writtenOrder(content: String): List<String> {
        val below = content.substringAfter("TEXT=\"P\"")
        return Regex("""<node ID="[^"]*" TEXT="([^"]*)"""")
            .findAll(below)
            .map { it.groupValues[1].ifEmpty { "-" } }
            .toList()
    }

    @Test
    fun summariesAreKeptInAMindMapViewFile() {
        val tree = tree()
        tree.summary("s", "b", "c")

        val read = MindMapViewFile.read(MindMapViewFile.write(tree))

        assertEquals(mapOf("S" to listOf("B", "C")), read.summaryTexts())
        assertEquals(listOf("A", "B", "C", "S", "D"), read.childTexts("P"))
    }

    @Test
    fun summaryOfSiblingsThatAreNotInTheFileIsAnOrdinaryNode() {
        val content = """
            <mindmapview version="1">
              <node id="r" text="root">
                <node id="a" text="A"/>
                <node id="s" text="S" summaryOf="gone"/>
              </node>
            </mindmapview>
        """.trimIndent()

        val read = MindMapViewFile.read(content)

        assertTrue(read.summaries.isEmpty())
        assertEquals(listOf("A", "S"), read.childTexts("root"))
    }

    @Test
    fun groupThatDoesNotStartTheSiblingsIsMarkedForFreeplane() {
        val tree = tree()
        tree.summary("s", "b", "c")

        val content = FreeMind.write(tree)

        assertEquals(listOf("A", "-", "B", "C", "S", "D"), writtenOrder(content))
        assertEquals(1, Regex("""<hook NAME="FirstGroupNode"/>""").findAll(content).count())
        assertEquals(1, Regex("""<hook NAME="SummaryNode"/>""").findAll(content).count())
        val read = FreeMind.read(content)
        assertEquals(mapOf("S" to listOf("B", "C")), read.summaryTexts())
        assertEquals(listOf("A", "B", "C", "S", "D"), read.childTexts("P"))
    }

    @Test
    fun groupsThatStartTheSiblingsOrFollowASummaryAreNotMarked() {
        val tree = tree()
        tree.summary("s", "a", "b")
        tree.summary("t", "c", "d")

        val content = FreeMind.write(tree)

        assertEquals(listOf("A", "B", "S", "C", "D", "T"), writtenOrder(content))
        assertFalse("FirstGroupNode" in content)
        assertEquals(mapOf("S" to listOf("A", "B"), "T" to listOf("C", "D")), FreeMind.read(content).summaryTexts())
    }

    @Test
    fun summaryIsWrittenRightAfterItsSiblingsWhereverItIs() {
        val tree = tree()
        tree.addNode("s", "p", "S", index = 0)
        tree.setSummary("s", setOf("a", "b"))

        val content = FreeMind.write(tree)

        assertEquals(listOf("A", "B", "S", "C", "D"), writtenOrder(content))
    }

    @Test
    fun overlappingSummaryIsWrittenAsAnOrdinaryNodeAfterTheGroups() {
        val tree = tree()
        tree.summary("s", "b")
        tree.summary("t", "b", "c")

        val content = FreeMind.write(tree)

        assertEquals(listOf("A", "-", "B", "S", "C", "D", "T"), writtenOrder(content))
        assertEquals(mapOf("S" to listOf("B")), FreeMind.read(content).summaryTexts())
    }

    @Test
    fun summariesOnEitherSideOfTheRootAreWrittenOnTheirSide() {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("l", rootId, "L", side = LayoutSide.LEFT)
            addNode("b", rootId, "B")
            addNode("m", rootId, "M", side = LayoutSide.LEFT)
            summary("s", "a", "b")
            summary("t", "m")
        }

        val read = FreeMind.read(FreeMind.write(tree))

        assertEquals(mapOf("S" to listOf("A", "B"), "T" to listOf("M")), read.summaryTexts())
        assertEquals(LayoutSide.LEFT, read.sideOf(read.node("T").id))
    }

    @Test
    fun freeplaneSummaryWithItsTextInANodeBelowItIsReadAsThatNode() {
        val content = """
            <map version="freeplane 1.11.1">
              <node ID="r" TEXT="root">
                <node ID="a" TEXT="A" POSITION="right"/>
                <node ID="b" TEXT="B" POSITION="right"/>
                <node ID="w" TEXT="" POSITION="right">
                  <hook NAME="SummaryNode"/>
                  <node ID="s" TEXT="Sum"><node ID="x" TEXT="Detail"/></node>
                </node>
              </node>
            </map>
        """.trimIndent()

        val read = FreeMind.read(content)

        assertEquals(mapOf("Sum" to listOf("A", "B")), read.summaryTexts())
        assertEquals(listOf("A", "B", "Sum"), read.childTexts("root"))
        assertEquals(listOf("Detail"), read.childTexts("Sum"))
    }

    @Test
    fun freeplaneSummaryOfSummariesIsAnOrdinaryNode() {
        val content = """
            <map version="freeplane 1.11.1">
              <node ID="r" TEXT="root">
                <node ID="a" TEXT="A"/>
                <node ID="s" TEXT="S"><hook NAME="SummaryNode"/></node>
                <node ID="t" TEXT="T"><hook NAME="SummaryNode"/></node>
                <node ID="b" TEXT="B"/>
              </node>
            </map>
        """.trimIndent()

        val read = FreeMind.read(content)

        assertEquals(mapOf("S" to listOf("A")), read.summaryTexts())
        assertEquals(listOf("A", "S", "T", "B"), read.childTexts("root"))
    }

    @Test
    fun freeplaneGroupStartWithTextIsANodeBeforeTheGroup() {
        val content = """
            <map version="freeplane 1.11.1">
              <node ID="r" TEXT="root">
                <node ID="a" TEXT="A"><hook NAME="FirstGroupNode"/></node>
                <node ID="b" TEXT="B"/>
                <node ID="s" TEXT="S"><hook NAME="SummaryNode"/></node>
              </node>
            </map>
        """.trimIndent()

        val read = FreeMind.read(content)

        assertEquals(mapOf("S" to listOf("B")), read.summaryTexts())
        assertEquals(listOf("A", "B", "S"), read.childTexts("root"))
    }

    @Test
    fun outlinesHaveASummaryAfterTheSiblingsItIsAbout() {
        val tree = tree()
        tree.summary("s", "b", "c")

        val outlines = listOf(Opml.write(tree), HtmlOutline.write(tree), Markdown.write(tree))

        outlines.forEach { outline ->
            val places = listOf("A", "B", "C", "S", "D").map { outline.indexOf(it, outline.indexOf("P") + 1) }
            assertEquals(places.sorted(), places, outline)
        }
    }
}
