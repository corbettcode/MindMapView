package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.BoundaryStyle
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.boundaryRect
import com.mindsync.library.util.Dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BoundaryDrawTest {
    private val green = Color(0xFF00A000)

    private var boundaryMenu: Boundary? = null
    private var nodeMenu: NodeData<*>? = null

    // The root with "a" and "b" below it, and "c" below "a", shown on the screen.
    private fun ComposeUiTest.shownState(): MindMapState {
        val tree = Tree<Any>(Dp(200f), Dp(300f)).apply {
            val rootId = getRootNode().id
            addNode("a", rootId, "A")
            addNode("b", rootId, "B")
            addNode("c", "a", "C")
        }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent {
            MindMap(
                state = state
                , modifier = Modifier.size(900.dp, 600.dp).testTag(TAG)
                , onBoundaryMenu = { boundary, _ -> boundaryMenu = boundary }
                , onNodeMenu = { node, _ -> nodeMenu = node }
            )
        }
        waitForIdle()
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun MindMapState.addBoundaryAround(
        id: String
        , vararg childIds: String
        , color: Color? = green
        , isFilled: Boolean = false
    ): Boundary {
        val parentId = tree.getNode(childIds.first()).parentId!!
        tree.addBoundary(Boundary(id, parentId, childIds.toSet(), color, BoundaryStyle.SOLID, isFilled))
        animateTreeChange()
        return tree.boundaries.first { it.id == id }
    }

    private fun ComposeUiTest.rectOf(
        state: MindMapState
        , boundary: Boundary
    ): Rect = assertNotNull(with(density) { boundaryRect(state.tree, boundary) })

    // Where a point of the tree is on the screen.
    private fun MindMapState.onScreen(point: Offset): Offset = point * scaleFactor + currentPoint

    private fun ImageBitmap.pixels(): List<Color> {
        val pixelMap = toPixelMap()
        return (0 until height).flatMap { y -> (0 until width).map { x -> pixelMap[x, y] } }
    }

    private fun Color.isNear(other: Color): Boolean =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    @Test
    fun boundaryIsDrawnInItsColor() = runComposeUiTest {
        val state = shownState()
        state.addBoundaryAround("x", "a")
        waitForIdle()

        val image = assertNotNull(state.exportImage())

        assertTrue(green in image.pixels(), "the outline is drawn in its color")
    }

    @Test
    fun filledBoundaryIsLightlyFilledWithItsColor() = runComposeUiTest {
        val state = shownState()
        val boundary = state.addBoundaryAround("x", "a", isFilled = true)
        waitForIdle()
        // A point in the padding inside the top edge of the boundary, where nothing else is drawn, as the line to
        // a node comes in at the middle of its side. It is in the pixels of the image, which is two of them a dp.
        val rect = rectOf(state, boundary)
        val bounds = with(density) { exportBounds(state.tree) }
        val inset = with(density) { 5.dp.toPx() }
        val inside = (Offset(rect.center.x, rect.top + inset) - bounds.topLeft) * (2f / density.density)

        val filled = assertNotNull(state.exportImage()).toPixelMap()[inside.x.toInt(), inside.y.toInt()]
        state.updateBoundary(state.tree.boundaries.single().copy(isFilled = false))
        waitForIdle()
        val notFilled = assertNotNull(state.exportImage()).toPixelMap()[inside.x.toInt(), inside.y.toInt()]

        // The fill is the color at 12% over the white background.
        val fill = Color(
            1f - 0.12f * (1f - green.red)
            , 1f - 0.12f * (1f - green.green)
            , 1f - 0.12f * (1f - green.blue)
        )
        assertTrue(filled.isNear(fill), "$filled is not the fill $fill")
        assertEquals(Color.White, notFilled)
    }

    @Test
    fun exportedImageHoldsAllOfTheBoundary() = runComposeUiTest {
        val state = shownState()
        val boundary = state.addBoundaryAround("x", "a")
        waitForIdle()

        val bounds = with(density) { exportBounds(state.tree) }
        val rect = rectOf(state, boundary)

        assertTrue(
            bounds.left < rect.left && bounds.top < rect.top && bounds.right > rect.right && bounds.bottom > rect.bottom
            , "$bounds does not hold $rect"
        )
    }

    @Test
    fun secondaryClickInsideABoundaryAsksForItsMenu() = runComposeUiTest {
        val state = shownState()
        val boundary = state.addBoundaryAround("x", "a")
        waitForIdle()
        val rect = rectOf(state, boundary)

        // The padding inside its left edge holds no node.
        val point = state.onScreen(Offset(rect.left + 4f, rect.center.y))
        onNodeWithTag(TAG).performMouseInput { rightClick(point) }
        waitForIdle()

        assertEquals("x", boundaryMenu?.id)
        assertNull(nodeMenu)
    }

    @Test
    fun secondaryClickOnANodeInsideABoundaryAsksForTheNodesMenu() = runComposeUiTest {
        val state = shownState()
        state.addBoundaryAround("x", "a")
        waitForIdle()
        val node = state.tree.getNode("a")
        val center = with(density) { Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx()) }

        onNodeWithTag(TAG).performMouseInput { rightClick(state.onScreen(center)) }
        waitForIdle()

        assertEquals("a", nodeMenu?.id)
        assertNull(boundaryMenu)
    }

    @Test
    fun innermostBoundaryIsTheOneFound() = runComposeUiTest {
        val state = shownState()
        state.addBoundaryAround("outer", "a")
        val inner = state.addBoundaryAround("inner", "c")
        waitForIdle()
        val rect = rectOf(state, inner)

        val found = state.findBoundary(state.onScreen(Offset(rect.left + 4f, rect.center.y)))

        assertEquals("inner", found?.id)
    }

    @Test
    fun boundaryOfAFoldedNodeIsNotDrawn() = runComposeUiTest {
        val state = shownState()
        state.addBoundaryAround("x", "c")
        state.tree.setFolded("a", true)
        state.animateTreeChange()
        waitForIdle()

        assertFalse(green in assertNotNull(state.exportImage()).pixels())
    }
}

private const val TAG = "mindMap"
