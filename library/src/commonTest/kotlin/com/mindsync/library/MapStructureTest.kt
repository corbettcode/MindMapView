package com.mindsync.library

import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.Tree
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapStructureTest {
    // The root with "a" on its right, "b" on its left and "c" on its upper left; "d" below "b", "f" floating.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "B", side = LayoutSide.LEFT)
        addNode("c", rootId, "C", side = LayoutSide.UPPER_LEFT)
        addNode("d", "b", "D")
        addNode("f", rootId, "F")
        setFloating("f", DpOffset(Dp(0f), Dp(300f)))
    }

    @Test
    fun mapIsAMindMapToStartWith() {
        assertEquals(MapStructure.MIND_MAP, tree().structure)
    }

    @Test
    fun structureIsSetAsOneChangeAndUndone() {
        val tree = tree()
        val state = MindMapState(tree).apply { setTree(tree) }

        state.setStructure(MapStructure.ORG_DOWN)
        assertEquals(MapStructure.ORG_DOWN, state.tree.structure)
        assertTrue(state.canUndo)

        state.undo()
        assertEquals(MapStructure.MIND_MAP, state.tree.structure)
        assertFalse(state.canUndo)
    }

    @Test
    fun structureAloneIsAChangeOfTheMap() {
        val tree = tree()
        val before = tree.snapshot()

        tree.structure = MapStructure.LOGIC_RIGHT

        assertFalse(before.hasSameContentAs(tree.snapshot()))
    }

    @Test
    fun mindMapGrowsByTheSideOfTheRoot() {
        val tree = tree()

        assertEquals(GrowDirection.RIGHT, MapStructure.MIND_MAP.directionOf(tree, "a"))
        assertEquals(GrowDirection.LEFT, MapStructure.MIND_MAP.directionOf(tree, "d"))
        assertEquals(GrowDirection.LEFT, MapStructure.MIND_MAP.directionOf(tree, "c"))
        assertEquals(GrowDirection.RIGHT, MapStructure.MIND_MAP.directionOf(tree, "f"))
    }

    @Test
    fun otherStructuresGrowOneWayFloatingTopicsToo() {
        val tree = tree()

        listOf("a", "b", "d", "f").forEach { id ->
            assertEquals(GrowDirection.RIGHT, MapStructure.LOGIC_RIGHT.directionOf(tree, id), id)
            assertEquals(GrowDirection.LEFT, MapStructure.LOGIC_LEFT.directionOf(tree, id), id)
            assertEquals(GrowDirection.DOWN, MapStructure.ORG_DOWN.directionOf(tree, id), id)
            assertEquals(GrowDirection.UP, MapStructure.ORG_UP.directionOf(tree, id), id)
        }
    }

    @Test
    fun timelineBranchesGrowUpAndDownByTurns() {
        val tree = tree()

        assertEquals(GrowDirection.RIGHT, MapStructure.TIMELINE.directionOf(tree, tree.getRootNode().id))
        assertEquals(GrowDirection.UP, MapStructure.TIMELINE.directionOf(tree, "a"))
        assertEquals(GrowDirection.DOWN, MapStructure.TIMELINE.directionOf(tree, "d"))
        assertEquals(GrowDirection.UP, MapStructure.TIMELINE.directionOf(tree, "c"))
        assertEquals(GrowDirection.DOWN, MapStructure.TIMELINE.directionOf(tree, "f"))
    }

    @Test
    fun fishbonesGrowAwayFromTheirHeadsAndMatricesIntoTheirCells() {
        val tree = tree()

        assertEquals(GrowDirection.RIGHT, MapStructure.FISHBONE_LEFT.directionOf(tree, "d"))
        assertEquals(GrowDirection.LEFT, MapStructure.FISHBONE_RIGHT.directionOf(tree, "d"))
        assertEquals(GrowDirection.RIGHT, MapStructure.MATRIX_ROWS.directionOf(tree, "d"))
        // In a matrix by columns the columns grow down, and the topics in their cells right, as logic charts.
        assertEquals(GrowDirection.DOWN, MapStructure.MATRIX_COLUMNS.directionOf(tree, "b"))
        assertEquals(GrowDirection.RIGHT, MapStructure.MATRIX_COLUMNS.directionOf(tree, "d"))
        assertTrue(MapStructure.FISHBONE_LEFT.isFishbone && MapStructure.FISHBONE_RIGHT.isFishbone)
        assertTrue(MapStructure.MATRIX_ROWS.isMatrix && MapStructure.MATRIX_COLUMNS.isMatrix)
        assertFalse(MapStructure.entries.any { it.isFishbone && it.isMatrix })
    }
}
