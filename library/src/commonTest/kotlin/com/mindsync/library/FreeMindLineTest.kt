package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreeMindLineTest {
    private fun readLine(
        edge: String
    ): NodeLine {
        val map = """
            <map version="1.0.1">
            <node ID="root" TEXT="Rocks">
            <node ID="node" TEXT="Granite" POSITION="right">
            $edge
            </node>
            </node>
            </map>
        """.trimIndent()
        return FreeMind.read(map).lineOf("node")
    }

    @Test
    fun freeMindEdgeIsRead() {
        assertEquals(
            NodeLine(NodeLineStyle.SHARP_LINEAR, Color.Red, NodeLineWidth.FOUR)
            , readLine("""<edge STYLE="sharp_linear" COLOR="#ff0000" WIDTH="4"/>""")
        )
    }

    @Test
    fun freeMindWidthsReadAsTheNearest() {
        fun width(value: String) = readLine("""<edge WIDTH="$value"/>""").width

        assertEquals(NodeLineWidth.THIN, width("thin"))
        assertEquals(NodeLineWidth.ONE, width("1"))
        assertEquals(NodeLineWidth.FOUR, width("3"))
        assertEquals(NodeLineWidth.EIGHT, width("6"))
        assertEquals(NodeLineWidth.EIGHT, width("20"))
        assertEquals(null, width("wide"))
    }

    @Test
    fun freeMindRootEdgeRoundTrips() {
        val map = """<map version="1.0.1"><node ID="root" TEXT="Rocks"><edge COLOR="#0000ff"/></node></map>"""
        val tree = FreeMind.read(map)
        val rootId = tree.getRootNode().id

        assertEquals(NodeLine(color = Color.Blue), tree.lineOf(rootId))

        val written = FreeMind.write(tree)
        assertTrue(written.contains("<edge COLOR=\"#0000ff\"/>"))
        assertEquals(NodeLine(color = Color.Blue), FreeMind.read(written).let { it.lineOf(it.getRootNode().id) })
    }

    @Test
    fun noEdgeIsWrittenWithoutSettings() {
        val tree = Tree<Any>()
        tree.addNode("a", tree.getRootNode().id, "<p>a</p>")

        assertFalse(FreeMind.write(tree).contains("<edge"))
    }

    @Test
    fun edgeSettingsRoundTrip() {
        val tree = Tree<Any>()
        tree.addNode("a", tree.getRootNode().id, "<p>a</p>")
        val line = NodeLine(NodeLineStyle.BEZIER, Color.Red, NodeLineWidth.THIN)
        tree.setLine("a", line)

        assertEquals(line, FreeMind.read(FreeMind.write(tree)).lineOf("a"))
    }
}
