package com.corbettcode.mindmapview.library

import androidx.compose.ui.unit.Density
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.model.GrowDirection
import com.corbettcode.mindmapview.library.model.MapStructure
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchTreeChartTest {
    // The root with "x", "a" and "y" on its right; "b", "c" and "d" below "a", and "e" and "f" below "c".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(600f)).apply {
        val rootId = getRootNode().id
        addNode("x", rootId, "X")
        addNode("a", rootId, "A")
        addNode("y", rootId, "Y")
        addNode("b", "a", "Branch B")
        addNode("c", "a", "Branch C")
        addNode("d", "a", "Branch D")
        addNode("e", "c", "Leaf E")
        addNode("f", "c", "Leaf F")
    }

    private fun laidOut(tree: Tree<Any>): MindMapState = MindMapState(tree).apply {
        setTree(tree)
        mindMapManager.arrangeTree()
    }

    private fun MindMapState.x(id: String) = tree.getNode(id).path.centerX.dpVal

    private fun MindMapState.y(id: String) = tree.getNode(id).path.centerY.dpVal

    private fun MindMapState.left(id: String) = x(id) - halfWidth(tree.getNode(id)).dpVal

    private fun MindMapState.right(id: String) = x(id) + halfWidth(tree.getNode(id)).dpVal

    private fun MindMapState.top(id: String) = y(id) - halfHeight(tree.getNode(id)).dpVal

    private fun MindMapState.bottom(id: String) = y(id) + halfHeight(tree.getNode(id)).dpVal

    @Test
    fun treeChartBranchHasItsColumnsInARowBelowItsTopic() {
        val state = laidOut(tree().apply { setBranchStructure("a", MapStructure.TREE_RIGHT) })

        with(state) {
            listOf("b", "c", "d").forEach { assertTrue(top(it) > bottom("a")) }
            assertTrue(right("b") < left("c") && maxOf(right("c"), right("e"), right("f")) < left("d"))
            assertEquals(x("a"), (left("b") + right("d")) / 2, 0.5f)
            val rootRight = right(tree.getRootNode().id)
            listOf("a", "b", "c", "d", "e", "f").forEach { id ->
                assertTrue(left(id) > rootRight, "$id is right of the root")
                assertTrue(top(id) > bottom("x") && bottom(id) < top("y"), "$id is between x and y")
            }
            // The topic's lines to its columns go down, as a tree chart's root's do.
            assertEquals(GrowDirection.DOWN, tree.placedDirectionOf("c"))
        }
    }

    @Test
    fun rowsAreIndentedBelowTheirColumnOnASpine() {
        val state = laidOut(tree().apply { setBranchStructure("a", MapStructure.TREE_RIGHT) })

        with(state) {
            assertTrue(top("e") > bottom("c") && top("f") > bottom("e"))
            assertEquals(left("c") + 30f, left("e"), 0.5f)
            val elbow = with(Density(1f)) { treeChartElbow(tree, tree.getNode("c"), tree.getNode("e")) }
            assertEquals(left("c") + 15f, elbow.first().x, 0.5f)
            assertEquals(left("e"), elbow.last().x, 0.5f)
            // Not the topic's own lines, which are curves down to its columns.
            assertTrue(tree.structureOf("a").isTreeChart && tree.structureOwnerOf("a") == "a")
        }
    }

    @Test
    fun summaryOfColumnsIsBelowThem() {
        val tree = tree().apply {
            setBranchStructure("a", MapStructure.TREE_RIGHT)
            addNode("s", "a", "Sum")
            setSummary("s", setOf("b", "c"))
        }
        val state = laidOut(tree)

        with(state) {
            assertEquals(GrowDirection.DOWN, tree.summaryDirectionOf("s"))
            assertTrue(top("s") > maxOf(bottom("b"), bottom("f")))
            assertEquals((left("b") + maxOf(right("c"), right("e"), right("f"))) / 2, x("s"), 0.5f)
        }
    }

    @Test
    fun columnsAreOrderedByXAndRowsByY() {
        val tree = tree().apply { setBranchStructure("a", MapStructure.TREE_RIGHT) }

        assertTrue(tree.isStackedAlongX("b"))
        assertFalse(tree.isStackedAlongX("e"))
    }

    @Test
    fun columnWithAStructureOfItsOwnTakesTheRoomOfItsBranch() {
        val tree = Tree<Any>(Dp(400f), Dp(100f)).apply {
            structure = MapStructure.TREE_RIGHT
            val rootId = getRootNode().id
            addNode("p", rootId, "P")
            addNode("q", rootId, "Q")
            addNode("p1", "p", "Leaf one")
            addNode("p2", "p", "Leaf two")
            setBranchStructure("p", MapStructure.LOGIC_RIGHT)
        }
        val state = laidOut(tree)

        with(state) {
            listOf("p1", "p2").forEach { assertTrue(left(it) > right("p")) }
            assertTrue(maxOf(right("p1"), right("p2")) < left("q"))
            // The column's leaves reach above it, and are still below the root.
            assertTrue(minOf(top("p1"), top("p2")) > bottom(tree.getRootNode().id))
        }
    }
}
