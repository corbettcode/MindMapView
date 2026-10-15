package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExportImageTest {
    // The root with [childCount] children, "child0" first.
    private fun ComposeUiTest.stateWithChildren(childCount: Int = 2): MindMapState {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        repeat(childCount) { tree.addNode("child$it", rootId, "child $it") }
        val state = MindMapState(tree)
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun ImageBitmap.pixels(): List<Color> {
        val pixelMap = toPixelMap()
        return (0 until height).flatMap { y -> (0 until width).map { x -> pixelMap[x, y] } }
    }

    @Test
    fun thereIsNoImageBeforeTheMapIsShown() {
        val state = MindMapState(Tree<Any>(Dp(150f), Dp(300f)))

        assertNull(state.exportImage())
    }

    @Test
    fun mapIsDrawnOnAWhiteBackground() = runComposeUiTest {
        val state = stateWithChildren()

        val image = assertNotNull(state.exportImage())

        val pixels = image.pixels()
        assertEquals(Color.White, image.toPixelMap()[0, 0])
        assertTrue(Color.LightGray in pixels, "the root is drawn")
        assertTrue(pixels.count { it != Color.White } > pixels.size / 100, "the nodes and lines are drawn")
    }

    @Test
    fun imageHoldsTheNodesOutOfViewAtTwoPixelsADp() = runComposeUiTest {
        val state = stateWithChildren(childCount = 30)
        val nodes = state.tree.nodes.values
        val top = nodes.minOf { it.path.centerY.dpVal }
        val bottom = nodes.maxOf { it.path.centerY.dpVal }
        assertTrue(bottom - top > 600f, "the nodes do not fit in the view")

        val image = assertNotNull(state.exportImage())

        assertTrue(image.height >= (bottom - top) * 2, "${image.height} pixels high")
    }

    @Test
    fun imageIsTheSameWhateverTheZoomAndPan() = runComposeUiTest {
        val state = stateWithChildren()
        val image = assertNotNull(state.exportImage())

        state.onTransform(Offset(300f, -200f), 3f)
        waitForIdle()
        val transformed = assertNotNull(state.exportImage())

        assertEquals(image.width, transformed.width)
        assertEquals(image.height, transformed.height)
        assertEquals(image.pixels(), transformed.pixels())
    }

    @Test
    fun imageMakesRoomForAnArrowLinkThatCurvesBeyondTheNodes() = runComposeUiTest {
        val state = stateWithChildren()
        val image = assertNotNull(state.exportImage())

        state.tree.addArrowLink(
            ArrowLink("link", "child0", "child1", startInclination = ArrowLinkInclination(Dp(0f), Dp(-400f)))
        )
        state.animateTreeChange()
        waitForIdle()
        val withLink = assertNotNull(state.exportImage())

        assertTrue(withLink.height > image.height + 200, "${withLink.height} against ${image.height}")
    }

    @Test
    fun largeImageIsScaledDownToItsLargestSide() = runComposeUiTest {
        val state = stateWithChildren()

        val image = assertNotNull(state.exportImage(maxSide = 100))

        assertEquals(100, max(image.width, image.height))
    }
}
