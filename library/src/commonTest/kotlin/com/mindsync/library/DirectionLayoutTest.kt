package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MindMapLayoutManager
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectionLayoutTest {
    // The root with "a", "b" and "c" below it, on both sides, and "d" and "e" below "b".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(600f), Dp(100f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "Bee", side = LayoutSide.LEFT)
        addNode("c", rootId, "C")
        addNode("d", "b", "D")
        addNode("e", "b", "E")
    }

    private fun Tree<Any>.arranged(direction: GrowDirection): Tree<Any> = apply {
        MindMapLayoutManager(LayoutSide.RIGHT, direction, isAllRootChildren = true).arrangeChildren(getRootNode(), this)
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal
    private fun Tree<*>.left(id: String) = getNode(id).let { x(id) - halfWidth(it).dpVal }
    private fun Tree<*>.right(id: String) = getNode(id).let { x(id) + halfWidth(it).dpVal }
    private fun Tree<*>.top(id: String) = getNode(id).let { y(id) - halfHeight(it).dpVal }
    private fun Tree<*>.bottom(id: String) = getNode(id).let { y(id) + halfHeight(it).dpVal }
    private fun Tree<*>.rootId() = getRootNode().id

    @Test
    fun childrenAreBelowTheirParentInARowInTheirOrder() {
        val tree = tree().arranged(GrowDirection.DOWN)

        listOf("a", "b", "c").forEach { assertTrue(tree.top(it) > tree.bottom(tree.rootId()), it) }
        assertEquals(tree.y("a"), tree.y("b"), 0.01f)
        assertTrue(tree.x("a") < tree.x("b") && tree.x("b") < tree.x("c"))
    }

    @Test
    fun theyAreAGapBelowTheirParent() {
        val tree = tree().arranged(GrowDirection.DOWN)

        assertEquals(tree.bottom(tree.rootId()) + 50f, tree.top("a"), 0.01f)
        assertEquals(tree.bottom("b") + 50f, tree.top("d"), 0.01f)
    }

    @Test
    fun theyAreCenteredOnTheirParent() {
        val tree = tree().arranged(GrowDirection.DOWN)

        assertEquals((tree.left("d") + tree.right("e")) / 2, tree.x("b"), 0.01f)
    }

    @Test
    fun branchesTakeTheirWidthSoNoneOverlap() {
        val tree = tree().arranged(GrowDirection.DOWN)

        // "b" is as wide as "d" and "e" side by side, so "a" and "c" are clear of them.
        assertTrue(tree.right("a") + 50f <= tree.left("d") + 0.01f)
        assertTrue(tree.right("e") + 50f <= tree.left("c") + 0.01f)
    }

    @Test
    fun upIsDownTurnedOver() {
        val down = tree().arranged(GrowDirection.DOWN)
        val up = tree().arranged(GrowDirection.UP)

        listOf("a", "b", "c", "d", "e").forEach { id ->
            assertEquals(down.x(id), up.x(id), 0.01f, id)
            assertEquals(down.y(id) - down.y(down.rootId()), up.y(up.rootId()) - up.y(id), 0.01f, id)
        }
    }

    @Test
    fun allChildrenOfTheRootAreTakenWhateverTheirSide() {
        val tree = tree().arranged(GrowDirection.RIGHT)

        listOf("a", "b", "c").forEach { assertTrue(tree.left(it) > tree.right(tree.rootId()), it) }
        assertTrue(tree.y("a") < tree.y("b") && tree.y("b") < tree.y("c"))
    }
}
