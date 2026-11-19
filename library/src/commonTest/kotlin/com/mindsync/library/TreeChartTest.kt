package com.mindsync.library

import androidx.compose.ui.unit.Density
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.TREE_CHART_INDENT
import com.mindsync.library.layout.summaryBracket
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TreeChartTest {
    private val density = Density(1f)
    private val indent = TREE_CHART_INDENT.dpVal

    // The root with "a" and "b" (on its left); "c" and "d" below "a", and "e" below "c".
    private fun tree(structure: MapStructure = MapStructure.TREE_RIGHT): Tree<Any> =
        Tree<Any>(Dp(600f), Dp(100f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("b", rootId, "B", side = LayoutSide.LEFT)
            addNode("c", "a", "C")
            addNode("d", "a", "D")
            addNode("e", "c", "E")
            this.structure = structure
        }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal
    private fun Tree<*>.left(id: String) = getNode(id).let { x(id) - halfWidth(it).dpVal }
    private fun Tree<*>.right(id: String) = getNode(id).let { x(id) + halfWidth(it).dpVal }
    private fun Tree<*>.top(id: String) = getNode(id).let { y(id) - halfHeight(it).dpVal }
    private fun Tree<*>.bottom(id: String) = getNode(id).let { y(id) + halfHeight(it).dpVal }
    private fun Tree<*>.rootId() = getRootNode().id

    @Test
    fun branchesAreColumnsInARowBelowTheRoot() {
        val tree = tree().arranged()

        assertEquals(tree.top("a"), tree.top("b"), 0.01f)
        assertTrue(tree.top("a") > tree.bottom(tree.rootId()))
        assertTrue(tree.right("e") < tree.left("b"), "the column of \"a\" is clear of \"b\"")
    }

    @Test
    fun rowsAreBelowTheirParentIndentedInOrder() {
        val tree = tree().arranged()

        assertEquals(tree.left("a") + indent, tree.left("c"), 0.01f)
        assertEquals(tree.left("c") + indent, tree.left("e"), 0.01f)
        assertTrue(tree.top("c") > tree.bottom("a"))
        assertTrue(tree.top("e") > tree.bottom("c"))
        assertTrue(tree.top("d") > tree.bottom("e"), "\"d\" is below the rows of \"c\"")
    }

    @Test
    fun leftChartIndentsToTheLeft() {
        val tree = tree(MapStructure.TREE_LEFT).arranged()

        assertEquals(tree.right("a") - indent, tree.right("c"), 0.01f)
        assertTrue(tree.nodes.values.all { it.path.centerX.dpVal > 0f }, "kept on the map")
    }

    @Test
    fun foldingShortensTheColumn() {
        val open = tree().arranged()
        val folded = tree().apply { setFolded("c", true) }.arranged()

        assertTrue(folded.top("d") < open.top("d"))
    }

    @Test
    fun rowLinesHangOffASpineBelowTheParent() {
        val tree = tree().arranged()

        val elbow = with(density) { treeChartElbow(tree, tree.getNode("a"), tree.getNode("c")) }

        assertEquals(3, elbow.size)
        assertEquals(tree.left("a") + indent / 2, elbow[0].x, 0.01f)
        assertEquals(tree.bottom("a"), elbow[0].y, 0.01f)
        assertEquals(elbow[0].x, elbow[1].x, 0.01f)
        assertEquals(tree.y("c"), elbow[1].y, 0.01f)
        assertEquals(tree.left("c"), elbow[2].x, 0.01f)
        val button = with(density) { addButtonCenter(tree, tree.getNode("a")) }
        assertEquals(elbow[0].x, button.x, 0.01f)
    }

    @Test
    fun summaryOfRowsIsToTheirRight() {
        val tree = tree().apply {
            addNode("s", "a", "S")
            setSummary("s", setOf("c", "d"))
        }.arranged()

        assertTrue(tree.left("s") > tree.right("e"))
        assertEquals((tree.top("c") + tree.bottom("d")) / 2, tree.y("s"), 0.01f)
        val bracket = assertNotNull(with(density) { summaryBracket(tree, "s") })
        assertTrue(bracket.isHorizontal)
    }

    @Test
    fun summaryOfColumnsIsBelowThem() {
        val tree = tree().apply {
            addNode("s", getRootNode().id, "S", index = 2)
            setSummary("s", setOf("a", "b"))
        }.arranged()

        assertTrue(tree.top("s") > tree.bottom("d"))
        assertEquals((tree.left("a") + tree.right("b")) / 2, tree.x("s"), 1f)
    }

    @Test
    fun droppedRowTakesItsPlaceByY() {
        val tree = tree().arranged()

        tree.moveAmongSiblings("d", Dp(tree.x("d") + 300f), Dp(tree.y("c") - 5f))

        assertEquals(listOf("d", "c"), tree.getNode("a").children)
    }
}
