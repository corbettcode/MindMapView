package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.model.GrowDirection
import com.corbettcode.mindmapview.library.model.MapStructure
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BranchDirectionTest {
    // The root with "x", "a" and "y" on its right; "b", "c" and "d" below "a", and "e" and "f" below "c".
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(600f)).apply {
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
        return MindMapState(tree).apply {
            setTree(tree)
            mindMapManager.arrangeTree()
        }
    }

    private fun MindMapState.setBranchStructure(
        id: String
        , structure: MapStructure?
    ) {
        select(tree.getNode(id))
        setBranchStructure(structure)
        mindMapManager.arrangeTree()
    }

    private fun MindMapState.x(id: String) = tree.getNode(id).path.centerX.dpVal

    private fun MindMapState.y(id: String) = tree.getNode(id).path.centerY.dpVal

    private fun MindMapState.left(id: String) = x(id) - halfWidth(tree.getNode(id)).dpVal

    private fun MindMapState.right(id: String) = x(id) + halfWidth(tree.getNode(id)).dpVal

    private fun MindMapState.top(id: String) = y(id) - halfHeight(tree.getNode(id)).dpVal

    private fun MindMapState.bottom(id: String) = y(id) + halfHeight(tree.getNode(id)).dpVal

    private val branchOfA = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun orgChartBranchIsARowBelowItsTopicClearOfItsParentAndSiblings() {
        val state = state()

        state.setBranchStructure("a", MapStructure.ORG_DOWN)

        with(state) {
            // "b", "c" and "d" are side by side in order below "a", centered on it.
            listOf("b", "c", "d").forEach { assertTrue(top(it) > bottom("a")) }
            assertTrue(right("b") < left("c") && right("c") < left("d"))
            assertEquals(x("a"), (left("b") + right("d")) / 2, 0.5f)
            // "c"'s children are below it.
            listOf("e", "f").forEach { assertTrue(top(it) > bottom("c")) }
            // The whole branch is right of the root, and between "x" above it and "y" below it.
            val rootRight = right(tree.getRootNode().id)
            branchOfA.forEach { id ->
                assertTrue(left(id) > rootRight, "$id is right of the root")
                assertTrue(top(id) > bottom("x"), "$id is below x")
                assertTrue(bottom(id) < top("y"), "$id is above y")
            }
        }
    }

    @Test
    fun logicChartLeftInARightBranchPushesItsTopicClear() {
        val state = state()

        state.setBranchStructure("a", MapStructure.LOGIC_LEFT)

        with(state) {
            listOf("b", "c", "d").forEach { assertTrue(right(it) < left("a")) }
            listOf("e", "f").forEach { assertTrue(right(it) < left("c")) }
            val rootRight = right(tree.getRootNode().id)
            branchOfA.forEach { id -> assertTrue(left(id) > rootRight, "$id is right of the root") }
        }
    }

    @Test
    fun ownerBelowAnOwnerIsLaidOutInItsOwnStructure() {
        val state = state()

        state.setBranchStructure("a", MapStructure.ORG_DOWN)
        state.setBranchStructure("c", MapStructure.ORG_UP)

        with(state) {
            // "c"'s children are above it, and still below "a", which pushes "c" down for them.
            listOf("e", "f").forEach { id ->
                assertTrue(bottom(id) < top("c"), "$id is above c")
                assertTrue(top(id) > bottom("a"), "$id is below a")
            }
            // "c"'s siblings are clear of its branch, side by side.
            assertTrue(right("b") < minOf(left("c"), left("e")))
            assertTrue(maxOf(right("c"), right("f")) < left("d"))
        }
    }

    @Test
    fun linesEnterATopicAsItsParentPlacesItAndLeaveItAsItsOwnStructureGrows() {
        val state = state()

        state.setBranchStructure("a", MapStructure.ORG_DOWN)

        with(state.tree) {
            assertEquals(GrowDirection.RIGHT, placedDirectionOf("a"))
            assertEquals(GrowDirection.DOWN, directionOf("a"))
            assertEquals(GrowDirection.DOWN, placedDirectionOf("c"))
            assertEquals(GrowDirection.RIGHT, placedDirectionOf("x"))
        }
    }

    @Test
    fun clearingTheBranchStructureGivesEveryPositionBack() {
        val state = state()
        val before = state.tree.nodes.mapValues { it.value.path.centerX.dpVal to it.value.path.centerY.dpVal }

        state.setBranchStructure("a", MapStructure.ORG_DOWN)
        state.setBranchStructure("a", null)

        val after = state.tree.nodes.mapValues { it.value.path.centerX.dpVal to it.value.path.centerY.dpVal }
        assertEquals(before, after)
    }
}
