package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogicChartTest {
    // The root with "a" on its right, "b" on its upper left and "c" on its left, and "d" below "b".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(300f)).apply {
        val rootId = getRootNode().id
        addNode("a", rootId, "A")
        addNode("b", rootId, "B", side = LayoutSide.UPPER_LEFT)
        addNode("c", rootId, "C", side = LayoutSide.LEFT)
        addNode("d", "b", "D")
    }

    private fun Tree<Any>.arranged(): Tree<Any> = apply {
        MindMapManager().apply {
            setTree(this@arranged)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal
    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal
    private fun Tree<*>.rootId() = getRootNode().id

    @Test
    fun allBranchesAreToTheRightInTheirOrder() {
        val tree = tree().apply { structure = MapStructure.LOGIC_RIGHT }.arranged()

        listOf("a", "b", "c").forEach { assertTrue(tree.x(it) > tree.x(tree.rootId()), it) }
        assertEquals(tree.x("a"), tree.x("c"), 0.01f)
        assertTrue(tree.y("a") < tree.y("b") && tree.y("b") < tree.y("c"))
        assertTrue(tree.x("d") > tree.x("b"))
    }

    @Test
    fun allBranchesAreToTheLeft() {
        val tree = tree().apply { structure = MapStructure.LOGIC_LEFT }.arranged()

        listOf("a", "b", "c").forEach { assertTrue(tree.x(it) < tree.x(tree.rootId()), it) }
        assertTrue(tree.x("d") < tree.x("b"))
        assertTrue(tree.nodes.values.all { it.path.centerX.dpVal > 0f }, "kept on the map")
    }

    @Test
    fun backToTheMindMapTheBranchesAreOnTheirSides() {
        val mindMap = tree().arranged()
        val tree = tree().apply { structure = MapStructure.LOGIC_RIGHT }.arranged()

        tree.structure = MapStructure.MIND_MAP
        tree.arranged()

        listOf("a", "b", "c", "d").forEach { id ->
            assertEquals(mindMap.x(id) - mindMap.x(mindMap.rootId()), tree.x(id) - tree.x(tree.rootId()), 0.01f, id)
            assertEquals(mindMap.y(id) - mindMap.y(mindMap.rootId()), tree.y(id) - tree.y(tree.rootId()), 0.01f, id)
        }
        assertEquals(LayoutSide.UPPER_LEFT, tree.sideOf("b"))
    }

    @Test
    fun theRootHasOneButtonAndMovingAroundIsForTheMindMap() {
        val tree = tree().apply { structure = MapStructure.LOGIC_RIGHT }
        val state = MindMapState(tree).apply { setTree(tree) }

        assertEquals(listOf(LayoutSide.RIGHT), state.addButtonSides(tree.getRootNode()))
        state.setStructure(MapStructure.MIND_MAP)
        assertEquals(LayoutSide.entries, state.addButtonSides(state.tree.getRootNode()))
    }
}
