package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.BoundaryStyle
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.HtmlOutline
import com.mindsync.library.io.Markdown
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.io.Opml
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundaryFilesTest {
    // The root with "p" on its right and "q" on its left; "b", "c" and "d" below "p".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
    }

    // The ids of the nodes of a tree that was read, by their text, as a file may give them other ids.
    private fun Tree<Any>.idOf(text: String): String = nodes.values.single { it.description == text }.id

    private fun Tree<Any>.textsOf(boundary: Boundary): List<String> =
        boundaryChildIds(boundary).map { getNode(it).description }

    @Test
    fun boundariesAreKeptInAMindMapViewFile() {
        val tree = tree()
        tree.addBoundary(Boundary("one", "p", setOf("b", "c"), Color(0xFF123456), BoundaryStyle.SOLID, false))
        tree.addBoundary(Boundary("two", tree.getRootNode().id, setOf("q")))

        val read = MindMapViewFile.read(MindMapViewFile.write(tree))

        val one = read.boundaries.single { it.id == "one" }
        assertEquals(read.idOf("P"), one.parentId)
        assertEquals(listOf("B", "C"), read.textsOf(one))
        assertEquals(Color(0xFF123456), one.color)
        assertEquals(BoundaryStyle.SOLID, one.style)
        assertFalse(one.isFilled)
        val two = read.boundaries.single { it.id == "two" }
        assertEquals(read.getRootNode().id, two.parentId)
        assertEquals(listOf("Q"), read.textsOf(two))
        assertNull(two.color)
        assertEquals(BoundaryStyle.DASHED, two.style)
        assertTrue(two.isFilled)
    }

    @Test
    fun boundaryOfNodesThatAreNotInTheFileIsLeftOut() {
        val content = """
            <mindmapview version="1">
              <node id="r" text="root">
                <node id="a" text="A"/>
              </node>
              <boundary id="x" parent="missing" children="a"/>
              <boundary id="y" parent="r" children="gone"/>
              <boundary id="z" parent="r" children="a gone"/>
            </mindmapview>
        """.trimIndent()

        val read = MindMapViewFile.read(content)

        assertEquals(listOf("z"), read.boundaries.map { it.id })
        assertEquals(listOf("A"), read.textsOf(read.boundaries.single()))
    }

    @Test
    fun mapWithoutBoundariesIsReadAsBefore() {
        val read = MindMapViewFile.read(MindMapViewFile.write(tree()))

        assertTrue(read.boundaries.isEmpty())
    }

    @Test
    fun boundaryIsAFreeMindCloudAroundEachOfItsNodes() {
        val tree = tree()
        tree.addBoundary(Boundary("one", "p", setOf("b", "c"), Color(0xFF123456)))

        val content = FreeMind.write(tree)

        assertEquals(2, Regex("<cloud COLOR=\"#123456\"/>").findAll(content).count())
        val read = FreeMind.read(content)
        assertEquals(listOf(listOf("B"), listOf("C")), read.boundaries.map { read.textsOf(it) })
        assertTrue(read.boundaries.all { it.color == Color(0xFF123456) && it.parentId == read.idOf("P") })
    }

    @Test
    fun boundaryInTheColorOfItsBranchIsACloudWithoutAColor() {
        val tree = tree()
        tree.addBoundary(Boundary("one", "p", setOf("c")))

        val content = FreeMind.write(tree)

        assertTrue("<cloud/>" in content)
        assertNull(FreeMind.read(content).boundaries.single().color)
    }

    @Test
    fun paleFreeMindCloudIsInTheColorOfItsBranch() {
        val content = """
            <map version="1.0.1">
              <node ID="r" TEXT="root">
                <cloud COLOR="#f0f0f0"/>
                <node ID="a" TEXT="A" POSITION="right"><cloud COLOR="#f0f0f0"/></node>
                <node ID="b" TEXT="B" POSITION="left"><cloud/></node>
                <node ID="c" TEXT="C" POSITION="left"><cloud COLOR="#335599"/></node>
              </node>
            </map>
        """.trimIndent()

        val read = FreeMind.read(content)

        // The root is in no boundary, as it has no parent.
        assertEquals(listOf(listOf("A"), listOf("B"), listOf("C")), read.boundaries.map { read.textsOf(it) })
        assertEquals(listOf(null, null, Color(0xFF335599)), read.boundaries.map { it.color })
        assertTrue(read.boundaries.all { it.style == BoundaryStyle.SOLID })
    }

    @Test
    fun outlinesAreWrittenWithoutTheirBoundaries() {
        val tree = tree()
        tree.addBoundary(Boundary("one", "p", setOf("b", "c")))
        val withoutBoundary = tree()

        assertEquals(Opml.write(withoutBoundary), Opml.write(tree))
        assertEquals(HtmlOutline.write(withoutBoundary), HtmlOutline.write(tree))
        assertEquals(Markdown.write(withoutBoundary), Markdown.write(tree))
    }
}
