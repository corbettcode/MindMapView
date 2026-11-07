package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.SUMMARY_BRACKET_GAP
import com.mindsync.library.layout.SUMMARY_BRACKET_WIDTH
import com.mindsync.library.layout.summaryBracket
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SummaryDrawTest {
    private val green = Color(0xFF00A000)

    // The root with "p" on its right and "q" on its left; "b" and "c" and their summary "s" below "p", and "f",
    // "g" and their summary "t" below "q". The summaries' lines are green, which no other line is.
    private fun ComposeUiTest.shownState(): MindMapState {
        val tree = Tree<Any>(Dp(450f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("p", rootId, "P")
            addNode("q", rootId, "Q", side = LayoutSide.LEFT)
            listOf("b", "c", "s").forEach { addNode(it, "p", it.uppercase()) }
            listOf("f", "g", "t").forEach { addNode(it, "q", it.uppercase()) }
            setSummary("s", setOf("b", "c"))
            setSummary("t", setOf("f", "g"))
            setLine("s", NodeLine(color = green))
            setLine("t", NodeLine(color = green))
        }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent { MindMap(state = state, modifier = Modifier.size(900.dp, 600.dp)) }
        waitForIdle()
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    // The pixels of the exported image, and where a point of the tree is in it: two pixels a dp.
    private fun ComposeUiTest.exported(state: MindMapState): Pair<PixelMap, (Offset) -> Offset> {
        val image = assertNotNull(state.exportImage())
        val bounds = with(density) { exportBounds(state.tree) }
        return image.toPixelMap() to { point: Offset -> (point - bounds.topLeft) * (2f / density.density) }
    }

    // Whether any pixel around the point is the color, as the edges of a line are blended with what is behind.
    private fun PixelMap.hasAround(
        point: Offset
        , color: Color
    ): Boolean = (-2..2).any { dx ->
        (-2..2).any { dy ->
            val x = point.x.toInt() + dx
            val y = point.y.toInt() + dy
            x in 0 until width && y in 0 until height && this[x, y] == color
        }
    }

    private fun ComposeUiTest.centerOf(
        state: MindMapState
        , id: String
    ): Offset = state.tree.getNode(id).let {
        with(density) { Offset(it.path.centerX.dpVal.dp.toPx(), it.path.centerY.dpVal.dp.toPx()) }
    }

    @Test
    fun bracketIsDrawnInTheColorOfTheSummarysLine() = runComposeUiTest {
        val state = shownState()
        val bracket = assertNotNull(with(density) { summaryBracket(state.tree, "s") })
        val (pixels, toImage) = exported(state)

        // Halfway down the straight part of the back of the bracket, above its point.
        val middleX = (bracket.backX + bracket.pointX) / 2
        val onBack = Offset(middleX, (bracket.top + bracket.pointY) / 2)
        assertTrue(pixels.hasAround(toImage(onBack), green), "the bracket is drawn")
        // On the line from its point to the summary.
        val onLine = Offset((bracket.pointX + bracket.nodeX) / 2, bracket.pointY)
        assertTrue(pixels.hasAround(toImage(onLine), green), "the bracket's line to the summary is drawn")
    }

    @Test
    fun noLineIsDrawnFromTheParentToTheSummary() = runComposeUiTest {
        val state = shownState()
        val (pixels, toImage) = exported(state)

        // A line from "p" to "s" would be green, halfway between them.
        val halfway = (centerOf(state, "p") + centerOf(state, "s")) / 2f
        assertFalse(pixels.hasAround(toImage(halfway), green), "a line is drawn from the parent")
    }

    @Test
    fun bracketIsPastTheSiblingsTowardsTheSummary() = runComposeUiTest {
        val state = shownState()
        val tree = state.tree
        fun edge(id: String, isRight: Boolean) = tree.getNode(id).let {
            with(density) { (it.path.centerX + if (isRight) halfWidth(it) else Dp(0f) - halfWidth(it)).dpVal.dp.toPx() }
        }

        val right = assertNotNull(with(density) { summaryBracket(tree, "s") })
        val left = assertNotNull(with(density) { summaryBracket(tree, "t") })

        with(density) {
            assertEquals(maxOf(edge("b", true), edge("c", true)) + SUMMARY_BRACKET_GAP.toPx(), right.backX, 0.01f)
            assertEquals(right.backX + SUMMARY_BRACKET_WIDTH.toPx(), right.pointX, 0.01f)
            assertEquals(edge("s", false), right.nodeX, 0.01f)
            assertEquals(minOf(edge("f", false), edge("g", false)) - SUMMARY_BRACKET_GAP.toPx(), left.backX, 0.01f)
            assertEquals(left.backX - SUMMARY_BRACKET_WIDTH.toPx(), left.pointX, 0.01f)
            assertEquals(edge("t", true), left.nodeX, 0.01f)
        }
        assertEquals(centerOf(state, "s").y, right.pointY, 0.01f)
    }

    @Test
    fun bracketOfAFoldedNodesSummaryIsNotDrawn() = runComposeUiTest {
        val state = shownState()
        state.tree.setFolded("p", true)
        state.tree.setFolded("q", true)
        state.animateTreeChange()
        waitForIdle()

        assertNull(with(density) { summaryBracket(state.tree, "s") })
        val pixels = assertNotNull(state.exportImage()).toPixelMap()
        val isGreenAnywhere = (0 until pixels.height).any { y -> (0 until pixels.width).any { pixels[it, y] == green } }
        assertFalse(isGreenAnywhere)
    }
}
