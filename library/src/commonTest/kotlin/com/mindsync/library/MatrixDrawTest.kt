package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapStructure
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MatrixDrawTest {
    // A project by rows Plan and Build, with Goals in Q1 and Risks in Q2 for Plan, and Library in Q1 for Build.
    private fun tree(): Tree<Any> = Tree<Any>(Dp(200f), Dp(150f)).apply {
        val rootId = getRootNode().id
        addNode("plan", rootId, "Plan")
        addNode("build", rootId, "Build")
        mapOf("goals" to ("plan" to "Q1"), "risks" to ("plan" to "Q2"), "library" to ("build" to "Q1"))
            .forEach { (id, place) ->
                addNode(id, place.first, id.replaceFirstChar { it.uppercase() })
                setLabel(id, place.second)
            }
        structure = MapStructure.MATRIX_ROWS
    }

    private fun ComposeUiTest.shownState(): MindMapState {
        val state = MindMapState(tree()).apply { setTree(tree) }
        setContent { MindMap(state = state, modifier = Modifier.size(900.dp, 700.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    @Test
    fun gridHasAColumnForEachLabelAndARowForEachRowAroundItsTopics() = runComposeUiTest {
        val state = shownState()
        val tree = state.tree
        val grid = assertNotNull(state.mindMapManager.matrixGridOf(tree.getRootNode()))

        assertEquals(listOf("Q1", "Q2"), grid.labels)
        assertEquals(4, grid.columnEdges.size)
        assertEquals(4, grid.rowEdges.size)
        tree.nodes.values.forEach { node ->
            val x = node.path.centerX.dpVal
            val y = node.path.centerY.dpVal
            assertTrue(x > grid.columnEdges.first() && x < grid.columnEdges.last(), node.id)
            assertTrue(y > grid.rowEdges.first() && y < grid.rowEdges.last(), node.id)
        }
        // Goals is in the cell of Plan's row and Q1's column.
        val goals = tree.getNode("goals").path
        assertTrue(goals.centerX.dpVal > grid.columnEdges[1] && goals.centerX.dpVal < grid.columnEdges[2])
        assertTrue(goals.centerY.dpVal > grid.rowEdges[1] && goals.centerY.dpVal < grid.rowEdges[2])
        assertTrue(tree.isMatrixCell("goals"))
        assertFalse(tree.isMatrixCell("plan"))
    }

    @Test
    fun tableOfAMatrixIsALineAlongEachEdgeOfItsGridHeavierPastTheHeaders() = runComposeUiTest {
        val state = shownState()
        val root = state.tree.getRootNode()
        val grid = assertNotNull(state.mindMapManager.matrixGridOf(root))

        val cells = assertNotNull(state.mindMapManager.tableCellsOf(root))
        assertEquals(grid.columnEdges.size + grid.rowEdges.size, cells.lines.size)
        assertEquals(
            setOf(grid.columnEdges[1] to true, grid.rowEdges[1] to false)
            , cells.lines.filter { it.isHeavy }.map { if (it.x1 == it.x2) it.x1 to true else it.y1 to false }.toSet()
        )
        assertEquals(grid.columnEdges.first(), cells.bounds.left)
        assertEquals(grid.rowEdges.last(), cells.bounds.bottom)
        assertNull(state.mindMapManager.tableCellsOf(state.tree.getNode("plan")))
    }

    @Test
    fun exportHasTheHeadersAndNoLinesToTheRowsAndCells() = runComposeUiTest {
        val state = shownState()

        val svg = assertNotNull(state.exportSvg())

        assertTrue("Q1" in svg && "Q2" in svg)
        // The label of a topic in a cell is its column's header, so its table of attributes leaves it out.
        assertFalse("Labels" in svg)
    }

    @Test
    fun topicDroppedInAnotherColumnTakesItsLabelAsOneChange() = runComposeUiTest {
        val state = shownState()
        val grid = assertNotNull(state.mindMapManager.matrixGridOf(state.tree.getRootNode()))
        val goals = state.tree.getNode("goals").path
        // Build's cell in Q2 is empty, so the drop is on no other topic.
        val toX = (grid.columnEdges[2] + grid.columnEdges[3]) / 2
        val toY = state.tree.getNode("library").path.centerY.dpVal

        runOnIdle {
            fun onScreen(x: Float, y: Float) = with(density) {
                Offset(x.dp.toPx(), y.dp.toPx())
            } * state.scaleFactor + state.currentPoint
            state.onTouchDown(onScreen(goals.centerX.dpVal, goals.centerY.dpVal))
            state.onNodeDrag(onScreen(toX, toY))
            state.onTouchUp({ _, _, _ -> })
        }
        waitForIdle()

        assertEquals("Q2", state.tree.labelOf("goals"))
        state.undo()
        assertEquals("Q1", state.tree.labelOf("goals"))
    }
}
