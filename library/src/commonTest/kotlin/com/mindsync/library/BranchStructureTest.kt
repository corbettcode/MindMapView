package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BranchStructureTest {
    // The root with "a" on its right and "x" on its left; "b" below "a", "c" below "b", and "p", "q", "r" below "c".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("x", rootId, "X", side = LayoutSide.LEFT)
        addNode("b", "a", "B")
        addNode("c", "b", "C")
        addNode("p", "c", "P")
        addNode("q", "c", "Q")
        addNode("r", "c", "R")
    }

    private fun state(): MindMapState {
        val tree = tree()
        return MindMapState(tree).apply {
            setTree(tree)
            mindMapManager.arrangeTree()
        }
    }

    @Test
    fun branchesFollowTheirParentsToStartWith() {
        val tree = tree()

        assertNull(tree.branchStructureOf("a"))
        assertEquals(tree.getRootNode().id, tree.structureOwnerOf("c"))
        assertEquals(MapStructure.MIND_MAP, tree.structureOf("c"))
    }

    @Test
    fun branchStructureIsSetAsOneChangeAndUndoneAndRedone() {
        val state = state()
        state.select(state.tree.getNode("b"))

        state.setBranchStructure(MapStructure.ORG_DOWN)
        assertEquals(MapStructure.ORG_DOWN, state.tree.branchStructureOf("b"))
        assertTrue(state.canUndo)

        state.undo()
        assertNull(state.tree.branchStructureOf("b"))
        assertFalse(state.canUndo)

        state.redo()
        assertEquals(MapStructure.ORG_DOWN, state.tree.branchStructureOf("b"))

        state.setBranchStructure(null)
        assertNull(state.tree.branchStructureOf("b"))
    }

    @Test
    fun ownerIsTheNearestTopicWithAStructure() {
        val tree = tree().apply {
            setBranchStructure("a", MapStructure.ORG_DOWN)
            setBranchStructure("c", MapStructure.LOGIC_LEFT)
        }

        assertEquals("a", tree.structureOwnerOf("a"))
        assertEquals("a", tree.structureOwnerOf("b"))
        assertEquals(MapStructure.ORG_DOWN, tree.structureOf("b"))
        assertEquals("c", tree.structureOwnerOf("p"))
        assertEquals(MapStructure.LOGIC_LEFT, tree.structureOf("p"))
        assertEquals(tree.getRootNode().id, tree.structureOwnerOf("x"))
    }

    @Test
    fun branchesGrowByTheirOwnersStructure() {
        val tree = tree().apply { setBranchStructure("a", MapStructure.ORG_DOWN) }

        assertEquals(GrowDirection.DOWN, tree.directionOf("a"))
        assertEquals(GrowDirection.DOWN, tree.directionOf("c"))
        assertEquals(GrowDirection.LEFT, tree.directionOf("x"))
        assertEquals(GrowDirection.RIGHT, tree.directionOf(tree.getRootNode().id))

        // A timeline set on "c" has its axis to its right, and the branches on it up and down by turns.
        tree.setBranchStructure("c", MapStructure.TIMELINE)
        assertEquals(GrowDirection.RIGHT, tree.directionOf("c"))
        assertEquals(listOf(GrowDirection.UP, GrowDirection.DOWN, GrowDirection.UP), listOf("p", "q", "r").map {
            tree.directionOf(it)
        })
        // Siblings on a timeline's axis are side by side, but those of a logic chart one above the other.
        assertTrue(tree.isStackedAlongX("q"))
        tree.setBranchStructure("c", MapStructure.LOGIC_RIGHT)
        assertFalse(tree.isStackedAlongX("q"))
    }

    @Test
    fun branchStructureIsKeptThroughCutAndPasteAndTheMapsStructure() {
        val state = state()
        state.select(state.tree.getNode("b"))
        state.setBranchStructure(MapStructure.TREE_RIGHT)

        state.setStructure(MapStructure.ORG_UP)
        assertEquals(MapStructure.TREE_RIGHT, state.tree.branchStructureOf("b"))

        state.cutNode()
        state.select(state.tree.getNode("x"))
        state.pasteNode()
        val pastedId = state.tree.getNode("x").children.single()
        assertEquals(MapStructure.TREE_RIGHT, state.tree.branchStructureOf(pastedId))
        assertNull(state.tree.branchStructureOf("b"))
    }

    @Test
    fun rootAndMindMapAreNoBranchStructures() {
        val tree = tree()

        assertFailsWith<IllegalArgumentException> {
            tree.setBranchStructure(tree.getRootNode().id, MapStructure.ORG_DOWN)
        }
        assertFailsWith<IllegalArgumentException> { tree.setBranchStructure("a", MapStructure.MIND_MAP) }

        val state = state()
        state.select(state.tree.getRootNode())
        state.setBranchStructure(MapStructure.ORG_DOWN)
        assertFalse(state.canUndo)
    }
}
