package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.model.GrowDirection
import com.corbettcode.mindmapview.library.model.MapStructure
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TreeTableLayoutTest {
    // Costs of a trip: Travel with Flights (Out, Back) and Train, Stay with Hotel, and Food on its own.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(400f), Dp(400f)).apply {
        val rootId = getRootNode().id
        addNode("travel", rootId, "Travel")
        addNode("flights", "travel", "Flights")
        addNode("out", "flights", "Out: 320")
        addNode("back", "flights", "Back: 290")
        addNode("train", "travel", "Train: 45")
        addNode("stay", rootId, "Stay")
        addNode("hotel", "stay", "Hotel, four nights: 560")
        addNode("food", rootId, "Food: 200")
        structure = MapStructure.TREE_TABLE
    }

    private fun manager(tree: Tree<Any>): MindMapManager = MindMapManager().apply {
        setTree(tree)
        arrangeTree()
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal

    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal

    private fun Tree<*>.left(id: String) = x(id) - halfWidth(getNode(id)).dpVal

    private fun Tree<*>.right(id: String) = x(id) + halfWidth(getNode(id)).dpVal

    private fun Tree<*>.top(id: String) = y(id) - halfHeight(getNode(id)).dpVal

    private fun Tree<*>.bottom(id: String) = y(id) + halfHeight(getNode(id)).dpVal

    @Test
    fun ownerIsAHeaderAcrossTheTopOfTheTable() {
        val tree = tree()
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getRootNode()))
        val rootId = tree.getRootNode().id

        assertEquals((grid.left + grid.right) / 2, tree.x(rootId), 0.5f)
        assertTrue(tree.top(rootId) > grid.headerTop && tree.bottom(rootId) < grid.rowEdges.first())
        tree.nodes.keys.filter { it != rootId }.forEach { id ->
            assertTrue(tree.top(id) > grid.rowEdges.first(), "$id is below the header")
            assertTrue(tree.left(id) > grid.left && tree.right(id) < grid.right, "$id is in the table")
        }
        assertEquals(GrowDirection.RIGHT, tree.directionOf("flights"))
    }

    @Test
    fun eachTopicIsInTheColumnOfItsDepthLeftOfItsChildrenAndAsTallAsThey() {
        val tree = tree()
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getRootNode()))

        assertEquals(4, grid.columnEdges.size)
        assertEquals(6, grid.rowEdges.size)
        val travel = grid.cellOf("travel")
        val flights = grid.cellOf("flights")
        assertEquals(grid.columnEdges[1], travel.right, 0.01f)
        assertEquals(travel.right, flights.left, 0.01f)
        // Travel spans Out, Back and Train; Flights only Out and Back.
        assertEquals(grid.cellOf("out").top, travel.top, 0.01f)
        assertEquals(grid.cellOf("train").bottom, travel.bottom, 0.01f)
        assertEquals(grid.cellOf("back").bottom, flights.bottom, 0.01f)
        assertTrue(tree.right("travel") < tree.left("flights") && tree.right("flights") < tree.left("out"))
        // Siblings are one below the other, in their order, each at the top of its cell.
        listOf("out" to "back", "back" to "train", "travel" to "stay", "stay" to "food").forEach { (one, next) ->
            assertTrue(tree.bottom(one) < tree.top(next), "$one above $next")
        }
        assertEquals(tree.y("travel"), tree.y("flights"), 0.5f)
    }

    @Test
    fun leavesOfEveryDepthEndInTheLastColumnAtTheRightOfTheTable() {
        val tree = tree()
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getRootNode()))

        listOf("out", "back", "train", "hotel", "food").forEach { id ->
            assertEquals(grid.right, grid.cellOf(id).right, 0.01f, id)
            assertEquals(tree.right("out"), tree.right(id), 0.5f, id)
        }
        // The other topics are at the left of their cells.
        assertEquals(tree.left("travel"), tree.left("stay"), 0.5f)
        assertEquals(grid.cellOf("flights").left + 10f, tree.left("flights"), 0.5f)
    }

    @Test
    fun foldedTopicIsALeaf() {
        val tree = tree().apply { setFolded("travel", true) }
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getRootNode()))

        assertEquals(3, grid.rowEdges.size - 1)
        assertFalse("flights" in grid.spans)
        assertEquals(grid.right, grid.cellOf("travel").right, 0.01f)
        assertEquals(tree.right("hotel"), tree.right("travel"), 0.5f)
    }

    @Test
    fun topicWithAStructureOfItsOwnIsALeafWithItsBranchInItsCell() {
        val tree = tree().apply { setBranchStructure("flights", MapStructure.ORG_DOWN) }
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getRootNode()))
        val cell = grid.cellOf("flights")

        assertTrue(grid.spans.getValue("flights").isLeaf)
        listOf("flights", "out", "back").forEach { id ->
            assertTrue(tree.left(id) > cell.left && tree.right(id) < cell.right, "$id is across its cell")
            assertTrue(tree.top(id) > cell.top && tree.bottom(id) < cell.bottom, "$id is down its cell")
        }
        assertTrue(tree.top("out") > tree.bottom("flights"))
    }

    @Test
    fun summaryIsPastTheTableBesideTheRowsItIsAbout() {
        val tree = tree().apply {
            addNode("most", "stay", "Most")
            setSummary("most", setOf("hotel"))
            addNode("all", getRootNode().id, "All")
            setSummary("all", setOf("travel", "stay"))
        }
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getRootNode()))

        assertFalse("most" in grid.spans)
        assertTrue(tree.left("most") > grid.right && tree.left("all") > grid.right)
        assertEquals((grid.cellOf("hotel").top + grid.cellOf("hotel").bottom) / 2, tree.y("most"), 0.5f)
        // The two are beside the same row, Hotel's, so the one placed later, below Stay, is past the root's.
        assertTrue(tree.left("most") > tree.right("all"))
    }

    @Test
    fun treeTableBranchIsClearOfItsSiblings() {
        val tree = Tree<Any>(Dp(400f), Dp(500f)).apply {
            val rootId = getRootNode().id
            addNode("above", rootId, "Above")
            addNode("costs", rootId, "Costs")
            addNode("below", rootId, "Below")
            addNode("travel", "costs", "Travel")
            addNode("train", "travel", "Train")
            addNode("food", "costs", "Food")
            setBranchStructure("costs", MapStructure.TREE_TABLE)
        }
        val grid = assertNotNull(manager(tree).treeTableGridOf(tree.getNode("costs")))

        val rootRight = tree.right(tree.getRootNode().id)
        assertTrue(grid.left > rootRight)
        assertTrue(grid.headerTop > tree.bottom("above") && grid.rowEdges.last() < tree.top("below"))
        assertEquals(tree.right("train"), tree.right("food"), 0.5f)
    }
}
