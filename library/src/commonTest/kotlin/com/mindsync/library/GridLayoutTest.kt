package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GridLayoutTest {
    private val mains = listOf("research", "build", "marketing", "sales", "support")

    // A plan of five main topics; Research with two below it, Build with App (and iOS below it) and API.
    private fun tree(columns: Int? = null): Tree<Any> = Tree<Any>(Dp(400f), Dp(400f)).apply {
        val rootId = getRootNode().id
        mains.forEach { addNode(it, rootId, it.replaceFirstChar { c -> c.uppercase() }) }
        addNode("users", "research", "Talk to users")
        addNode("size", "research", "Market size")
        addNode("app", "build", "App")
        addNode("ios", "app", "iOS")
        addNode("api", "build", "API")
        structure = MapStructure.GRID
        setGridColumns(rootId, columns)
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
    fun ownerIsAHeaderAndTheMainTopicsFillRowsOfFourToStartWith() {
        val tree = tree()
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getRootNode()))
        val rootId = tree.getRootNode().id

        assertEquals(4, grid.columns)
        assertEquals((grid.left + grid.right) / 2, tree.x(rootId), 0.5f)
        assertTrue(tree.bottom(rootId) < grid.rowEdges.first())
        listOf("research", "build", "marketing", "sales").zipWithNext().forEach { (one, next) ->
            assertEquals(tree.y(one), tree.y(next), 0.5f, "$one and $next share a row")
            assertTrue(tree.right(one) < tree.left(next), "$one before $next")
        }
        assertTrue(tree.top("support") > tree.bottom("users"))
        assertEquals(tree.x("research"), tree.x("support"), 0.5f)
        assertEquals(GrowDirection.DOWN, tree.directionOf(rootId))
        assertEquals(GrowDirection.RIGHT, tree.directionOf("research"))
    }

    @Test
    fun fewerTopicsThanColumnsAreOneRowOfAsMany() {
        val tree = tree().apply { listOf("marketing", "sales", "support").forEach { removeNode(it) } }

        assertEquals(2, assertNotNull(manager(tree).gridCellsOf(tree.getRootNode())).columns)
    }

    @Test
    fun twoColumnsWrapFiveTopicsIntoThreeRowsOfEqualColumns() {
        val tree = tree(columns = 2)
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getRootNode()))

        assertEquals(3, grid.columnEdges.size)
        assertEquals(grid.columnEdges[1] - grid.columnEdges[0], grid.columnEdges[2] - grid.columnEdges[1], 0.01f)
        assertEquals(tree.x("research"), tree.x("marketing"), 0.5f)
        assertEquals(tree.x("marketing"), tree.x("support"), 0.5f)
        assertTrue(tree.y("research") < tree.y("marketing") && tree.y("marketing") < tree.y("support"))
        assertEquals(tree.y("marketing"), tree.y("sales"), 0.5f)
    }

    @Test
    fun topicsBelowAMainTopicAreInTheLowerPartOfItsCellAsALogicChart() {
        val tree = tree()
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getRootNode()))
        val head = grid.headOf(1)
        val body = assertNotNull(grid.bodyOf(1))

        assertEquals((head.left + head.right) / 2, tree.x("build"), 0.5f)
        assertEquals(head.bottom, body.top, 0.01f)
        listOf("app", "ios", "api").forEach { id ->
            assertTrue(tree.left(id) > body.left && tree.right(id) < body.right, "$id is across the cell")
            assertTrue(tree.top(id) > body.top && tree.bottom(id) < body.bottom, "$id is down the cell")
        }
        assertEquals(body.left + 10f, tree.left("app"), 0.5f)
        assertTrue(tree.left("ios") > tree.right("app") && tree.top("api") > tree.bottom("app"))
        // Sales has nothing below it, but its row's other cell does, so its cell has an empty lower part.
        assertNotNull(grid.bodyOf(3))
    }

    @Test
    fun rowWithNothingBelowItsTopicsHasCellsOfOnePart() {
        val tree = tree(columns = 2)
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getRootNode()))

        assertNull(grid.bodyOf(2))
        assertNull(grid.bodyOf(4))
        assertEquals(1 + 2 + 1 + 1, grid.rowEdges.size)
    }

    @Test
    fun mainTopicWithAStructureOfItsOwnHasItsBranchInItsCell() {
        val tree = tree().apply { setBranchStructure("build", MapStructure.ORG_DOWN) }
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getRootNode()))
        val head = grid.headOf(1)

        listOf("build", "app", "ios", "api").forEach { id ->
            assertTrue(tree.left(id) > head.left && tree.right(id) < head.right, "$id is across the cell")
            assertTrue(tree.top(id) > head.top && tree.bottom(id) < head.bottom, "$id is down the cell")
        }
        assertTrue(tree.top("app") > tree.bottom("build"))
    }

    @Test
    fun summaryOfMainTopicsIsBelowTheGridAndOneInACellIsInIt() {
        val tree = tree().apply {
            addNode("all", getRootNode().id, "All")
            setSummary("all", setOf("research", "build"))
            addNode("both", "research", "Both")
            setSummary("both", setOf("users", "size"))
        }
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getRootNode()))
        val body = assertNotNull(grid.bodyOf(0))

        assertTrue(tree.top("all") > grid.bottom)
        assertEquals((grid.left + grid.right) / 2, tree.x("all"), 0.5f)
        assertEquals(GrowDirection.DOWN, tree.directionOf("all"))
        assertTrue(tree.left("both") > tree.right("users") && tree.right("both") < body.right)
    }

    @Test
    fun gridBranchIsClearOfItsSiblingsWithItsTopicAtTheNearEndOfItsHeader() {
        val tree = Tree<Any>(Dp(400f), Dp(500f)).apply {
            val rootId = getRootNode().id
            addNode("above", rootId, "Above")
            addNode("plan", rootId, "Plan")
            addNode("below", rootId, "Below")
            listOf("a", "b", "c").forEach { addNode(it, "plan", it.uppercase()) }
            addNode("a1", "a", "A1")
            setBranchStructure("plan", MapStructure.GRID)
        }
        val grid = assertNotNull(manager(tree).gridCellsOf(tree.getNode("plan")))

        assertTrue(grid.left > tree.right(tree.getRootNode().id))
        assertEquals(grid.left + 10f, tree.left("plan"), 0.5f)
        assertTrue(grid.headerTop > tree.bottom("above") && grid.bottom < tree.top("below"))
    }

    @Test
    fun mainTopicDraggedIsPutInReadingOrder() {
        val tree = tree(columns = 2)
        manager(tree)

        // Dropped on the right of the last row, after Support.
        tree.moveAmongSiblings("research", Dp(tree.right("support") + 50f), Dp(tree.y("support")))
        assertEquals(listOf("build", "marketing", "sales", "support", "research"), tree.getRootNode().children)
        // Dropped in the first row, between Build and Marketing, which is on the right of it.
        tree.moveAmongSiblings("sales", Dp(tree.right("build") + 1f), Dp(tree.y("build")))
        assertEquals(listOf("build", "sales", "marketing", "support", "research"), tree.getRootNode().children)
    }
}
