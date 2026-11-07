package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.BoundaryStyle
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundaryTreeTest {
    // The root with "p" on its right and "q" on its left; "b", "c", "d" and "e" below "p", and "f" below "q".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("p", rootId, "P")
        addNode("q", rootId, "Q", side = LayoutSide.LEFT)
        listOf("b", "c", "d", "e").forEach { addNode(it, "p", it.uppercase()) }
        addNode("f", "q", "F")
    }

    private fun boundary(
        parentId: String
        , vararg childIds: String
    ) = Boundary("x", parentId, childIds.toSet())

    private fun Tree<Any>.only(): Boundary = boundaries.single()

    @Test
    fun boundaryIsAroundItsChildrenAndThoseBetweenThem() {
        val tree = tree()

        tree.addBoundary(boundary("p", "d", "b"))

        assertEquals(listOf("b", "c", "d"), tree.boundaryChildIds(tree.only()))
        assertEquals(setOf("b", "c", "d"), tree.only().childIds)
    }

    @Test
    fun childAddedBetweenItsChildrenIsInsideIt() {
        val tree = tree()
        tree.addBoundary(boundary("p", "b", "d"))

        tree.addNode("n", "p", "N", index = 2)

        assertEquals(listOf("b", "c", "n", "d"), tree.boundaryChildIds(tree.only()))
    }

    @Test
    fun removedChildrenAreLeftOut() {
        val first = tree().apply {
            addBoundary(boundary("p", "b", "d"))
            removeNode("b")
        }
        val last = tree().apply {
            addBoundary(boundary("p", "b", "d"))
            removeNode("d")
        }
        val middle = tree().apply {
            addBoundary(boundary("p", "b", "d"))
            removeNode("c")
        }

        assertEquals(listOf("c", "d"), first.boundaryChildIds(first.only()))
        assertEquals(listOf("b", "c"), last.boundaryChildIds(last.only()))
        assertEquals(listOf("b", "d"), middle.boundaryChildIds(middle.only()))
    }

    @Test
    fun boundaryWithNoChildrenLeftIsGone() {
        val tree = tree()
        tree.addBoundary(boundary("p", "c"))

        tree.removeNode("c")

        assertTrue(tree.boundaries.isEmpty())
    }

    @Test
    fun childMovedToAnotherParentLeavesTheBoundary() {
        val tree = tree()
        tree.addBoundary(boundary("p", "b", "c"))

        tree.removeNode("c")
        tree.attachNode("c", "q")

        assertEquals(setOf("b"), tree.only().childIds)
    }

    @Test
    fun boundaryBelowARemovedNodeIsGone() {
        val tree = tree()
        tree.addBoundary(boundary("p", "b", "c"))

        tree.removeNode("p")

        assertTrue(tree.boundaries.isEmpty())
    }

    @Test
    fun underTheRootABoundaryIsOnOneSide() {
        val tree = tree()
        val rootId = tree.getRootNode().id

        tree.addBoundary(boundary(rootId, "p", "q"))

        assertEquals(setOf("p"), tree.only().childIds)
        assertEquals(listOf("p"), tree.boundaryChildIds(tree.only()))
    }

    @Test
    fun underTheRootTheChildrenBetweenAreThoseOnItsSide() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addNode("r", rootId, "R")

        tree.addBoundary(boundary(rootId, "p", "r"))

        assertEquals(listOf("p", "r"), tree.boundaryChildIds(tree.only()))
    }

    @Test
    fun childMovedToTheOtherSideOfTheRootLeavesTheBoundary() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addNode("r", rootId, "R")
        tree.addBoundary(boundary(rootId, "p", "r"))

        tree.moveToOtherSide("r")

        assertEquals(setOf("p"), tree.only().childIds)
    }

    @Test
    fun boundaryAroundNoChildOfItsParentIsNotAdded() {
        val tree = tree()

        assertFailsWith<IllegalArgumentException> { tree.addBoundary(boundary("p", "f")) }
        assertFailsWith<IllegalArgumentException> { tree.addBoundary(boundary("missing", "b")) }
        assertTrue(tree.boundaries.isEmpty())
    }

    @Test
    fun boundaryWithTheSameIdReplacesTheOther() {
        val tree = tree()
        tree.addBoundary(boundary("p", "b"))

        tree.addBoundary(boundary("p", "c").copy(color = Color.Red, style = BoundaryStyle.SOLID, isFilled = false))

        val boundary = tree.only()
        assertEquals(setOf("c"), boundary.childIds)
        assertEquals(Color.Red, boundary.color)
        assertEquals(BoundaryStyle.SOLID, boundary.style)
        assertFalse(boundary.isFilled)
    }

    @Test
    fun boundaryIsRemoved() {
        val tree = tree()
        tree.addBoundary(boundary("p", "b"))

        tree.removeBoundary("x")

        assertTrue(tree.boundaries.isEmpty())
    }

    @Test
    fun boundariesArePartOfTheMapAndComeBackWithIt() {
        val tree = tree()
        val before = tree.snapshot()

        tree.addBoundary(boundary("p", "b", "c"))
        val withBoundary = tree.snapshot()
        tree.restore(before)

        assertFalse(before.hasSameContentAs(withBoundary))
        assertTrue(tree.boundaries.isEmpty())
        tree.restore(withBoundary)
        assertEquals(listOf("b", "c"), tree.boundaryChildIds(tree.only()))
    }

    @Test
    fun aChangeOfAChildThatLeavesTheBoundaryChangesTheMap() {
        val tree = tree()
        tree.addBoundary(boundary("p", "b", "c"))
        val before = tree.snapshot()

        tree.updateBoundaryColorFor("x", Color.Blue)

        assertFalse(before.hasSameContentAs(tree.snapshot()))
    }

    private fun Tree<Any>.updateBoundaryColorFor(
        id: String
        , color: Color
    ) = addBoundary(boundaries.first { it.id == id }.copy(color = color))
}
