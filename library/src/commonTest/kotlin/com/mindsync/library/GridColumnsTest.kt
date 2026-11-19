package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridColumnsTest {
    // The root with "a" and "x"; "b" below "a" and "c" below "b".
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("x", rootId, "X")
            addNode("b", "a", "B")
            addNode("c", "b", "C")
        }
        return MindMapState(tree).apply {
            setTree(tree)
            mindMapManager.arrangeTree()
        }
    }

    @Test
    fun gridHasAsManyColumnsAsFitToStartWith() {
        val tree = state().tree

        assertNull(tree.gridColumnsOf(tree.getRootNode().id))
        assertNull(tree.gridColumnsOf("a"))
    }

    @Test
    fun columnsOfTheMapsGridAreSetFromAnyTopicInItAsOneChange() {
        val state = state()
        val rootId = state.tree.getRootNode().id
        state.setStructure(MapStructure.GRID)
        state.select(state.tree.getNode("c"))

        state.setGridColumns(3)
        assertEquals(3, state.tree.gridColumnsOf(rootId))
        assertNull(state.tree.gridColumnsOf("c"))

        state.undo()
        assertNull(state.tree.gridColumnsOf(rootId))
        state.redo()
        assertEquals(3, state.tree.gridColumnsOf(rootId))

        state.setGridColumns(null)
        assertNull(state.tree.gridColumnsOf(rootId))
    }

    @Test
    fun columnsOfABranchsGridAreSetOnItsOwner() {
        val state = state()
        state.select(state.tree.getNode("b"))
        state.setBranchStructure(MapStructure.GRID)

        state.select(state.tree.getNode("c"))
        state.setGridColumns(2)
        assertEquals(2, state.tree.gridColumnsOf("b"))

        state.select(state.tree.getNode("b"))
        state.setGridColumns(4)
        assertEquals(4, state.tree.gridColumnsOf("b"))
    }

    @Test
    fun columnsOfTheGridsOfTheSelectedTopicsAreToldAndNoneForTopicsInNoGrid() {
        val state = state()
        state.select(state.tree.getNode("c"))
        assertEquals(emptySet(), state.selectedGridColumns())

        state.setStructure(MapStructure.GRID)
        assertEquals(setOf(null), state.selectedGridColumns())
        state.setGridColumns(2)
        assertEquals(setOf<Int?>(2), state.selectedGridColumns())
    }

    @Test
    fun topicsInNoGridAreLeftAlone() {
        val state = state()
        state.select(state.tree.getNode("c"))

        state.setGridColumns(3)
        assertFalse(state.canUndo)
        assertTrue(state.tree.nodes.keys.all { state.tree.gridColumnsOf(it) == null })
    }

    @Test
    fun columnsGoWithTheirTopicAndAreKeptThroughCutAndPaste() {
        val state = state()
        state.tree.setBranchStructure("b", MapStructure.GRID)
        state.tree.setGridColumns("b", 2)
        state.select(state.tree.getNode("b"))

        state.cutNode()
        assertNull(state.tree.gridColumnsOf("b"))
        state.select(state.tree.getNode("x"))
        state.pasteNode()
        assertEquals(2, state.tree.gridColumnsOf(state.tree.getNode("x").children.single()))
    }

    @Test
    fun gridHasAtLeastOneColumn() {
        val tree = state().tree

        assertFailsWith<IllegalArgumentException> { tree.setGridColumns("a", 0) }
    }
}
