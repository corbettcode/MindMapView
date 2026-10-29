package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.Tree
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeLineTest {
    private val drawInfo = DrawInfo()
    private val rootColor = Color.Gray

    private fun tree(): Tree<Any> {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "a")
        tree.addNode("a1", "a", "a1")
        tree.addNode("b", rootId, "b")
        return tree
    }

    private fun resolve(tree: Tree<Any>): Map<String, ResolvedLine> {
        val lines = mutableMapOf<String, ResolvedLine>()
        forEachNodeWithLine(tree, drawInfo, rootColor) { node: NodeData<*>, line -> lines[node.id] = line }
        return lines
    }

    @Test
    fun nothingSetLooksAsToday() {
        val tree = tree()
        val lines = resolve(tree)
        val rootChildren = tree.getRootNode().children
        rootChildren.forEachIndexed { index, id ->
            val expected = ResolvedLine(NodeLineStyle.BEZIER, rootLineColor(drawInfo, index), drawInfo.lineWidth)
            assertEquals(expected, lines[id])
        }
        assertEquals(lines.getValue("a").color, lines.getValue("a1").color)
    }

    @Test
    fun ownSettingAppliesToItsBranch() {
        val tree = tree()
        tree.setLine("a", NodeLine(style = NodeLineStyle.LINEAR))
        val lines = resolve(tree)
        assertEquals(NodeLineStyle.LINEAR, lines.getValue("a").style)
        assertEquals(NodeLineStyle.LINEAR, lines.getValue("a1").style)
        assertEquals(NodeLineStyle.BEZIER, lines.getValue("b").style)
    }

    @Test
    fun settingsAreInheritedSeparately() {
        val tree = tree()
        tree.setLine("a", NodeLine(color = Color.Red))
        tree.setLine("a1", NodeLine(width = NodeLineWidth.EIGHT))
        assertEquals(ResolvedLine(NodeLineStyle.BEZIER, Color.Red, Dp(8f)), resolve(tree).getValue("a1"))
    }

    @Test
    fun rootSettingsReachEveryBranch() {
        val tree = tree()
        tree.setLine(tree.getRootNode().id, NodeLine(color = Color.Blue))
        val lines = resolve(tree)
        listOf("a", "a1", "b").forEach { assertEquals(Color.Blue, lines.getValue(it).color) }
    }

    @Test
    fun rootStyleAndWidthReachEveryBranch() {
        val tree = tree()
        tree.setLine(tree.getRootNode().id, NodeLine(style = NodeLineStyle.LINEAR, width = NodeLineWidth.EIGHT))
        val lines = resolve(tree)
        listOf("a", "a1", "b").forEach {
            assertEquals(NodeLineStyle.LINEAR, lines.getValue(it).style)
            assertEquals(Dp(8f), lines.getValue(it).width)
        }
    }

    @Test
    fun branchColorWinsOverRootColor() {
        val tree = tree()
        tree.setLine(tree.getRootNode().id, NodeLine(color = Color.Blue))
        tree.setLine("a", NodeLine(color = Color.Red))
        val lines = resolve(tree)
        assertEquals(Color.Red, lines.getValue("a").color)
        assertEquals(Color.Red, lines.getValue("a1").color)
        assertEquals(Color.Blue, lines.getValue("b").color)
    }

    @Test
    fun emptyLineMakesTheNodeInheritAgain() {
        val tree = tree()
        tree.setLine("a", NodeLine(NodeLineStyle.LINEAR))
        tree.setLine("a", NodeLine())
        assertEquals(NodeLineStyle.BEZIER, resolve(tree).getValue("a").style)
        assertTrue(tree.lineOf("a").isEmpty)
    }

    @Test
    fun snapshotKeepsLines() {
        val tree = tree()
        tree.setLine("a", NodeLine(style = NodeLineStyle.SHARP_BEZIER))
        val snapshot = tree.snapshot()
        tree.setLine("b", NodeLine(width = NodeLineWidth.TWO))
        assertFalse(snapshot.hasSameContentAs(tree.snapshot()))
        tree.restore(snapshot)
        assertTrue(tree.lineOf("b").isEmpty)
        assertEquals(NodeLineStyle.SHARP_BEZIER, tree.lineOf("a").style)
    }
}
