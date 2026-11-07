package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BoundaryStateTest {
    // The root with "p" and "r" on its right and "q" on its left; "b", "c" and "d" below "p", and "e" below "b".
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            val rootId = getRootNode().id
            addNode("p", rootId, "P")
            addNode("q", rootId, "Q", side = LayoutSide.LEFT)
            addNode("r", rootId, "R")
            listOf("b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
            addNode("e", "b", "E")
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    private fun MindMapState.selectNodes(vararg ids: String) {
        select(tree.getNode(ids.first()))
        ids.drop(1).forEach { toggleSelected(it) }
    }

    private fun MindMapState.childrenOf(id: String) = tree.getNode(id).children

    @Test
    fun boundaryIsAroundTheSelectedSiblings() {
        val state = state()
        state.selectNodes("b", "d")

        val added = state.addBoundary()

        assertEquals(1, added.size)
        assertEquals("p", added.single().parentId)
        assertEquals(listOf("b", "c", "d"), state.tree.boundaryChildIds(added.single()))
        assertEquals(added, state.boundaries)
    }

    @Test
    fun eachParentAndSideGetsABoundaryOfItsOwn() {
        val state = state()
        state.selectNodes("b", "e", "p", "q", "r")

        val added = state.addBoundary()

        val rootId = state.tree.getRootNode().id
        // "b" and "e" are below "p", so "p" stands for their branch.
        assertEquals(
            setOf(rootId to setOf("p", "r"), rootId to setOf("q"))
            , added.map { it.parentId to it.childIds }.toSet()
        )
    }

    @Test
    fun theRootIsInNoBoundary() {
        val state = state()
        state.select(state.tree.getRootNode())

        val added = state.addBoundary()

        assertTrue(added.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun withNothingSelectedNoBoundaryIsAdded() {
        val state = state()
        state.select(null)

        assertTrue(state.addBoundary().isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun addingBoundariesIsOneChange() {
        val state = state()
        state.selectNodes("b", "q")

        state.addBoundary()
        state.undo()

        assertTrue(state.boundaries.isEmpty())
        assertFalse(state.canUndo)
        state.redo()
        assertEquals(2, state.boundaries.size)
    }

    @Test
    fun boundaryIsChangedAndRemovedAsChangesOfTheirOwn() {
        val state = state()
        state.selectNodes("c")
        val boundary = state.addBoundary().single()

        state.updateBoundary(boundary.copy(color = Color.Red))
        assertEquals(Color.Red, state.boundaries.single().color)
        state.removeBoundary(boundary.id)
        assertTrue(state.boundaries.isEmpty())

        state.undo()
        assertEquals(Color.Red, state.boundaries.single().color)
        state.undo()
        assertEquals(null, state.boundaries.single().color)
    }

    @Test
    fun aBoundaryThatIsNotThereIsNotChanged() {
        val state = state()
        state.selectNodes("c")
        val boundary = state.addBoundary().single()
        state.removeBoundary(boundary.id)
        val wasUndoable = state.canUndo

        state.updateBoundary(boundary.copy(color = Color.Red))

        assertTrue(state.boundaries.isEmpty())
        assertEquals(wasUndoable, state.canUndo)
    }

    @Test
    fun copiedBranchIsPastedWithTheBoundariesWithinIt() {
        val state = state()
        state.selectNodes("c", "d")
        state.addBoundary()
        state.selectNodes("p")
        state.copyNode()

        state.selectNodes("q")
        state.pasteNode()

        val pastedP = state.childrenOf("q").single()
        val pasted = state.boundaries.single { it.parentId == pastedP }
        val pastedChildren = state.childrenOf(pastedP)
        assertEquals(pastedChildren.drop(1), state.tree.boundaryChildIds(pasted))
        assertNotEquals(state.boundaries.first { it.parentId == "p" }.id, pasted.id)
    }

    @Test
    fun boundaryAroundTheCopiedNodesThemselvesIsNotPasted() {
        val state = state()
        state.selectNodes("c", "d")
        state.addBoundary()
        state.copyNode()

        state.selectNodes("q")
        state.pasteNode()

        assertEquals(2, state.childrenOf("q").size)
        assertEquals(1, state.boundaries.size)
    }

    @Test
    fun boundaryOfACutBranchGoesWithIt() {
        val state = state()
        state.selectNodes("c")
        state.addBoundary()

        state.selectNodes("p")
        state.cutNode()

        assertTrue(state.boundaries.isEmpty())
        state.undo()
        assertEquals(1, state.boundaries.size)
    }
}
