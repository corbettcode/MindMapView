package com.corbettcode.mindmapview.library

import androidx.compose.ui.graphics.Color
import com.corbettcode.mindmapview.library.data.ArrowLink
import com.corbettcode.mindmapview.library.data.ArrowLinkInclination
import com.corbettcode.mindmapview.library.data.ArrowLinkLineStyle
import com.corbettcode.mindmapview.library.data.ArrowLinkThickness
import com.corbettcode.mindmapview.library.data.CircleNodeData
import com.corbettcode.mindmapview.library.data.NodeAttribute
import com.corbettcode.mindmapview.library.data.NodeBorder
import com.corbettcode.mindmapview.library.data.NodeBorderStyle
import com.corbettcode.mindmapview.library.data.NodeLine
import com.corbettcode.mindmapview.library.data.NodeLineStyle
import com.corbettcode.mindmapview.library.data.NodeLineWidth
import com.corbettcode.mindmapview.library.data.NodeShape
import com.corbettcode.mindmapview.library.data.OvalNodeData
import com.corbettcode.mindmapview.library.data.StadiumNodeData
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.io.FreeMind
import com.corbettcode.mindmapview.library.io.MindMapViewFile
import com.corbettcode.mindmapview.library.model.AttributeDisplay
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.model.NoteDisplay
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MindMapViewFileTest {
    private fun richTree(): Tree<Any> {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.updateNodeColor(rootId, Color.Blue)
        tree.updateNodeNote(rootId, "<p>root note</p>")
        tree.addNode("a", rootId, "<p>some <b>bold</b><br>text &amp; \"quotes\"</p>", NodeShape.STADIUM, Color.Red, Color.White, LayoutSide.UPPER_LEFT, "<p>line one</p>\n<p>line\ttwo</p>")
        tree.addNode("b", rootId, "<p>below right</p>", NodeShape.OVAL, Color(0x80FF00FF), side = LayoutSide.LOWER_RIGHT)
        tree.addNode("c", rootId, "<p>left</p>", NodeShape.CIRCLE, side = LayoutSide.LEFT)
        tree.addNode("d", rootId, "<p>right</p>")
        tree.addNode("a1", "a", "<p>child</p>")
        tree.addNode("a2", "a", "<p>other child</p>")
        tree.setFolded("a", true)
        tree.setAttributes("b", listOf(NodeAttribute("size", "<large>"), NodeAttribute("", "")))
        tree.addArrowLink(ArrowLink("link1", "a1", "d"))
        tree.addArrowLink(
            ArrowLink(
                "link2", "c", rootId, Color.Green, hasStartArrow = true, hasEndArrow = false
                , startInclination = ArrowLinkInclination(Dp(-12.5f), Dp(40f))
                , endInclination = ArrowLinkInclination(Dp(3f), Dp(-7.25f))
                , lineStyle = ArrowLinkLineStyle.DASH_DOT
                , thickness = ArrowLinkThickness.THICK
            )
        )
        return tree
    }

    @Test
    fun roundTripKeepsEverything() {
        val tree = richTree()
        val rootId = tree.getRootNode().id

        val written = MindMapViewFile.write(tree)
        val read = MindMapViewFile.read(written)

        assertEquals(written, MindMapViewFile.write(read))
        val root = read.getRootNode()
        assertEquals(Color.Blue, root.color)
        assertEquals("<p>root note</p>", root.note)
        assertEquals(tree.getRootNode().description, root.description)
        assertEquals(listOf("a", "b", "c", "d"), root.children)
        assertEquals(
            listOf(LayoutSide.UPPER_LEFT, LayoutSide.LOWER_RIGHT, LayoutSide.LEFT, LayoutSide.RIGHT)
            , root.children.map { read.sideOf(it) }
        )
        assertEquals(LayoutSide.UPPER_LEFT, read.sideOf("a2"))

        val a = assertIs<StadiumNodeData>(read.getNode("a"))
        assertEquals("<p>some <b>bold</b><br>text &amp; \"quotes\"</p>", a.description)
        assertEquals("<p>line one</p>\n<p>line\ttwo</p>", a.note)
        assertEquals(Color.Red, a.color)
        assertEquals(Color.White, a.textColor)
        assertEquals(listOf("a1", "a2"), a.children)
        assertTrue(read.isFolded("a"))
        assertFalse(read.isFolded("b"))

        val b = assertIs<OvalNodeData>(read.getNode("b"))
        assertEquals(Color(0x80FF00FF), b.color)
        assertEquals(listOf(NodeAttribute("size", "<large>"), NodeAttribute("", "")), read.attributesOf("b"))
        assertIs<CircleNodeData>(read.getNode("c"))

        assertEquals(tree.arrowLinks, read.arrowLinks)
        assertEquals(rootId, read.arrowLinks[1].targetId)
    }

    @Test
    fun aDraggedRootIsKeptWhereItWasPut() {
        val tree = richTree()
        tree.moveRoot(Dp(321.5f), Dp(-40f))

        val read = MindMapViewFile.read(MindMapViewFile.write(tree))

        assertTrue(read.isRootDragged)
        assertEquals(Dp(321.5f), read.getRootNode().path.centerX)
        assertEquals(Dp(-40f), read.getRootNode().path.centerY)
        assertFalse(MindMapViewFile.read(MindMapViewFile.write(richTree())).isRootDragged)
    }

    @Test
    fun aFreeMindMapReadIsWrittenWithAllItHas() {
        val imported = FreeMind.read(FreeMind.write(richTree()))

        val read = MindMapViewFile.read(MindMapViewFile.write(imported))

        // FreeMind only knows left and right.
        assertEquals(LayoutSide.LEFT, read.sideOf("a"))
        assertEquals(LayoutSide.RIGHT, read.sideOf("b"))
        assertEquals(FreeMind.write(imported), FreeMind.write(read))
    }

    @Test
    fun aFreeMindMapKeepsTheLineStylesOfItsArrowLinks() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>a</p>")
        ArrowLinkLineStyle.entries.forEach { lineStyle ->
            tree.addArrowLink(ArrowLink(lineStyle.name, rootId, "a", lineStyle = lineStyle))
        }

        val read = FreeMind.read(FreeMind.write(tree))

        assertEquals(ArrowLinkLineStyle.entries, read.arrowLinks.map { it.lineStyle })
    }

    @Test
    fun aFreeMindMapKeepsTheThicknessesOfItsArrowLinks() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>a</p>")
        ArrowLinkThickness.entries.forEach { thickness ->
            tree.addArrowLink(ArrowLink(thickness.name, rootId, "a", thickness = thickness))
        }

        val read = FreeMind.read(FreeMind.write(tree))

        assertEquals(ArrowLinkThickness.entries, read.arrowLinks.map { it.thickness })
    }

    @Test
    fun anArrowLinkWithoutALineStyleOrThicknessIsSolidAndNormal() {
        val tree = Tree<Any>()
        tree.addNode("a", tree.getRootNode().id, "<p>a</p>")
        tree.addArrowLink(ArrowLink("link", tree.getRootNode().id, "a"))
        val written = MindMapViewFile
            .write(tree)
            .replace(" lineStyle=\"solid\"", "")
            .replace(" thickness=\"normal\"", "")

        val link = MindMapViewFile.read(written).arrowLinks.single()
        assertEquals(ArrowLinkLineStyle.SOLID, link.lineStyle)
        assertEquals(ArrowLinkThickness.NORMAL, link.thickness)
    }

    @Test
    fun readingSomethingThatIsNotAMindMapViewMapFails() {
        assertFailsWith<IllegalArgumentException> { MindMapViewFile.read("just some text") }
        assertFailsWith<IllegalArgumentException> { MindMapViewFile.read(FreeMind.write(richTree())) }
        assertFailsWith<IllegalArgumentException> { MindMapViewFile.read("<mindmapview version=\"1\"></mindmapview>") }
    }

    @Test
    fun linesRoundTrip() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>a</p>")
        val line = NodeLine(NodeLineStyle.SHARP_BEZIER, Color(0x80FF0000), NodeLineWidth.THIN)
        tree.setLine("a", line)
        tree.setLine(rootId, NodeLine(color = Color.Blue))

        val read = MindMapViewFile.read(MindMapViewFile.write(tree))

        assertEquals(line, read.lineOf("a"))
        assertEquals(NodeLine(color = Color.Blue), read.lineOf(read.getRootNode().id))
    }

    @Test
    fun noteDisplaysRoundTrip() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>a</p>")
        tree.addNode("b", rootId, "<p>b</p>")
        tree.setNoteDisplay("a", NoteDisplay.INLINE)
        tree.setNoteDisplay(rootId, NoteDisplay.HIDDEN)

        val written = MindMapViewFile.write(tree)
        val read = MindMapViewFile.read(written)

        assertEquals(NoteDisplay.INLINE, read.noteDisplayOf("a"))
        assertNull(read.noteDisplayOf("b"))
        assertEquals(NoteDisplay.HIDDEN, read.noteDisplayOf(read.getRootNode().id))
    }

    @Test
    fun attributeDisplaysRoundTrip() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>a</p>")
        tree.addNode("b", rootId, "<p>b</p>")
        tree.setAttributeDisplay("a", AttributeDisplay.FLYOVER)
        tree.setAttributeDisplay(rootId, AttributeDisplay.HIDDEN)

        val read = MindMapViewFile.read(MindMapViewFile.write(tree))

        assertEquals(AttributeDisplay.FLYOVER, read.attributeDisplayOf("a"))
        assertNull(read.attributeDisplayOf("b"))
        assertEquals(AttributeDisplay.HIDDEN, read.attributeDisplayOf(read.getRootNode().id))
    }

    @Test
    fun bordersRoundTrip() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>a</p>")
        tree.addNode("b", rootId, "<p>b</p>")
        val border = NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.FOUR, Color(0x80FF0000))
        tree.setBorder("a", border)
        tree.setBorder(rootId, NodeBorder(NodeBorderStyle.LINE))

        val written = MindMapViewFile.write(tree)
        val read = MindMapViewFile.read(written)

        assertEquals(border, read.borderOf("a"))
        assertEquals(NodeBorder(), read.borderOf("b"))
        assertEquals(NodeBorder(NodeBorderStyle.LINE), read.borderOf(read.getRootNode().id))
        assertEquals(2, Regex(" border=").findAll(written).count())
    }

    @Test
    fun borderSavedWithoutAColorIsInTheColorOfTheText() {
        val text = "<mindmapview version=\"1\"><node id=\"r\" text=\"r\">" +
            "<node id=\"a\" text=\"a\" textColor=\"#ff112233\" border=\"line\" borderWidth=\"4\"/></node></mindmapview>"

        val read = MindMapViewFile.read(text)

        assertEquals(NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.FOUR, Color(0xFF112233)), read.borderOf("a"))
    }

    @Test
    fun fileWithoutLinesHasNone() {
        val read = MindMapViewFile.read(MindMapViewFile.write(richTree()))

        assertTrue(read.nodes.keys.all { read.lineOf(it).isEmpty })
    }

    @Test
    fun unknownLineValuesAreInherited() {
        val text = "<mindmapview version=\"1\"><node id=\"r\" text=\"r\">" +
            "<node id=\"a\" text=\"a\" lineStyle=\"zigzag\" lineWidth=\"wide\" lineColor=\"nope\"/>" +
            "</node></mindmapview>"

        assertEquals(NodeLine(), MindMapViewFile.read(text).lineOf("a"))
    }

    @Test
    fun imagesAreKeptByTheirAddresses() {
        val tree = Tree<Any>()
        val images = mapOf(
            "https://example.com/a.png?size=1&shape=round" to byteArrayOf(-119, 80, 78, 71, 0, 1, 2)
            , "https://example.com/b.jpg" to byteArrayOf()
        )

        val read = MindMapViewFile.readImages(MindMapViewFile.write(tree, images))

        assertEquals(images.keys, read.keys)
        images.forEach { (src, bytes) -> assertContentEquals(bytes, read[src]) }
    }

    @Test
    fun anImageThatIsNoBase64IsLeftOut() {
        val text = "<mindmapview version=\"1\"><node id=\"r\" text=\"r\"/>" +
            "<image src=\"https://example.com/a.png\">not base64!</image></mindmapview>"

        assertEquals(emptyMap(), MindMapViewFile.readImages(text))
    }
}
