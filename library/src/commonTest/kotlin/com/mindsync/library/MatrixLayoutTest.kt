package com.mindsync.library

import com.mindsync.library.data.LABELS_ATTRIBUTE
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.Tree
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatrixLayoutTest {
    // A project by rows Plan, Build and Ship; its topics in quarters by their labels, and "misc" with none.
    private fun tree(structure: MapStructure = MapStructure.MATRIX_ROWS): Tree<Any> =
        Tree<Any>(Dp(400f), Dp(400f)).apply {
            val rootId = getRootNode().id
            listOf("plan", "build", "ship").forEach { addNode(it, rootId, it.replaceFirstChar { c -> c.uppercase() }) }
            mapOf(
                "goals" to ("plan" to "Q1"), "risks" to ("plan" to "Q2"), "library" to ("build" to "Q1")
                , "app" to ("build" to "Q1"), "docs" to ("build" to "Q2"), "misc" to ("build" to null)
                , "release" to ("ship" to "Q3")
            ).forEach { (id, place) ->
                addNode(id, place.first, id.replaceFirstChar { it.uppercase() })
                setLabel(id, place.second)
            }
            this.structure = structure
        }

    private fun laidOut(tree: Tree<Any>): Tree<Any> = tree.apply {
        MindMapManager().apply {
            setTree(tree)
            arrangeTree()
        }
    }

    private fun Tree<*>.x(id: String) = getNode(id).path.centerX.dpVal

    private fun Tree<*>.y(id: String) = getNode(id).path.centerY.dpVal

    private fun Tree<*>.left(id: String) = x(id) - halfWidth(getNode(id)).dpVal

    private fun Tree<*>.right(id: String) = x(id) + halfWidth(getNode(id)).dpVal

    private fun Tree<*>.top(id: String) = y(id) - halfHeight(getNode(id)).dpVal

    private fun Tree<*>.bottom(id: String) = y(id) + halfHeight(getNode(id)).dpVal

    @Test
    fun labelIsTheFirstOfTheLabelsAndSettingItKeepsTheOthers() {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            addNode("a", getRootNode().id, "A")
            setAttributes("a", listOf(NodeAttribute("Owner", "Ann"), NodeAttribute(LABELS_ATTRIBUTE, " Q1 , urgent")))
        }

        assertEquals("Q1", tree.labelOf("a"))
        tree.setLabel("a", "Q2")
        assertEquals("Q2, urgent", tree.attributesOf("a").last().value)
        assertEquals("Owner", tree.attributesOf("a").first().name)
        tree.setLabel("a", null)
        assertNull(tree.labelOf("a"))
        assertEquals(listOf("Owner"), tree.attributesOf("a").map { it.name })
    }

    @Test
    fun topicsAreInTheColumnsOfTheirLabelsInTheOrderTheyAreFirstMet() {
        val tree = laidOut(tree())

        // Q1, then Q2, then Q3, then the column with no header.
        assertEquals(tree.left("goals"), tree.left("library"), 0.5f)
        assertEquals(tree.left("goals"), tree.left("app"), 0.5f)
        assertEquals(tree.left("risks"), tree.left("docs"), 0.5f)
        assertTrue(maxOf(tree.right("goals"), tree.right("library"), tree.right("app")) < tree.left("risks"))
        assertTrue(maxOf(tree.right("risks"), tree.right("docs")) < tree.left("release"))
        assertTrue(tree.right("release") < tree.left("misc"))
    }

    @Test
    fun rowsAreOneBelowTheOtherWithTheirTopicsInTheirRowStackedInACell() {
        val tree = laidOut(tree())
        val rootId = tree.getRootNode().id

        listOf("plan", "build", "ship").forEach { assertEquals(tree.x(rootId), tree.x(it), 0.5f) }
        assertTrue(tree.bottom(rootId) < tree.top("plan"))
        assertTrue(tree.y("plan") < tree.y("build") && tree.y("build") < tree.y("ship"))
        // Library and App share a cell, one below the other, and the row is as tall as both.
        assertTrue(tree.top("app") > tree.bottom("library"))
        assertTrue(tree.bottom("goals") < tree.top("library") && tree.bottom("app") < tree.top("release"))
        // The row headers are left of the cells.
        assertTrue(tree.right("build") < tree.left("library"))
        assertEquals(GrowDirection.RIGHT, tree.directionOf("build"))
        assertFalse(tree.isStackedAlongX("build"))
    }

    @Test
    fun matrixByColumnsIsTheSameTableTurned() {
        val tree = laidOut(tree(MapStructure.MATRIX_COLUMNS))
        val rootId = tree.getRootNode().id

        listOf("plan", "build", "ship").forEach { assertEquals(tree.y(rootId), tree.y(it), 0.5f) }
        assertTrue(tree.right(rootId) < tree.left("plan"))
        assertTrue(tree.x("plan") < tree.x("build") && tree.x("build") < tree.x("ship"))
        // Q1 is the first row below the headers, Q2 the next, then Q3, then the one with no header.
        assertEquals(tree.top("goals"), tree.top("library"), 0.5f)
        assertTrue(tree.bottom("app") < tree.top("risks") && tree.top("risks") == tree.top("docs"))
        assertTrue(tree.bottom("docs") < tree.top("release") && tree.bottom("release") < tree.top("misc"))
        assertEquals(GrowDirection.DOWN, tree.directionOf("build"))
        assertTrue(tree.isStackedAlongX("build"))
    }

    @Test
    fun aFoldedRowHasNoCells() {
        val tree = tree().apply { setFolded("build", true) }
        laidOut(tree)

        // Without Build's topics, Misc is gone from the table and Q3 follows Q2 straight after Plan's Risks.
        assertTrue(tree.right("risks") < tree.left("release"))
        assertTrue(tree.bottom("plan") < tree.top("build") && tree.bottom("build") < tree.top("ship"))
    }

    @Test
    fun matrixBranchIsClearOfItsSiblings() {
        val tree = Tree<Any>(Dp(400f), Dp(500f)).apply {
            val rootId = getRootNode().id
            addNode("above", rootId, "Above")
            addNode("table", rootId, "Table")
            addNode("below", rootId, "Below")
            listOf("r1", "r2").forEach { row ->
                addNode(row, "table", row)
                addNode("$row-a", row, "Cell A")
                addNode("$row-b", row, "Cell B")
                setLabel("$row-a", "A")
                setLabel("$row-b", "B")
            }
            setBranchStructure("table", MapStructure.MATRIX_ROWS)
        }
        laidOut(tree)

        val rootRight = tree.right(tree.getRootNode().id)
        listOf("table", "r1", "r2", "r1-a", "r1-b", "r2-a", "r2-b").forEach { id ->
            assertTrue(tree.left(id) > rootRight, "$id is right of the root")
            assertTrue(tree.top(id) > tree.bottom("above") && tree.bottom(id) < tree.top("below"), "$id is between")
        }
        assertTrue(tree.right("r1-a") < tree.left("r1-b"))
    }
}
