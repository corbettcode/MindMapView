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
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.NodeLineWidth
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
        assertTrue(Tree.ROOT_COLOR in pixels, "the root is drawn")
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

    @Test
    fun chosenLineColorIsDrawn() = runComposeUiTest {
        val state = stateWithChildren()
        val chosen = Color(0xFFFF0000)
        assertTrue(chosen !in assertNotNull(state.exportImage()).pixels(), "no line is in the chosen color yet")

        NodeLineStyle.entries.forEach { style ->
            state.tree.setLine("child0", NodeLine(style = style, color = chosen))

            val pixels = assertNotNull(state.exportImage()).pixels()

            assertTrue(chosen in pixels, "a $style line is drawn in the chosen color")
        }
    }

    @Test
    fun sharpLineNarrowsFromItsParentToItsNode() = runComposeUiTest {
        val state = stateWithChildren(childCount = 1)
        val chosen = Color(0xFFFF0000)
        state.tree.addNode("grandchild", "child0", "grandchild")
        state.animateTreeChange()
        waitForIdle()

        fun chosenPerColumn(line: NodeLine): List<Int> {
            state.tree.setLine("grandchild", line.copy(color = chosen, width = NodeLineWidth.EIGHT))
            val image = assertNotNull(state.exportImage())
            val pixelMap = image.toPixelMap()
            val counts = (0 until image.width).map { x ->
                (0 until image.height).count { y -> pixelMap[x, y] == chosen }
            }
            return counts.subList(counts.indexOfFirst { it > 0 }, counts.indexOfLast { it > 0 } + 1)
        }
        // The most pixels high the line is near each of its ends, the wider end first.
        fun List<Int>.ends(): Pair<Int, Int> {
            val ends = listOf(take(size / 10).max(), takeLast(size / 10).max())
            return ends.max() to ends.min()
        }

        val (bezierWide, bezierNarrow) = chosenPerColumn(NodeLine(style = NodeLineStyle.BEZIER)).ends()
        val (sharpWide, sharpNarrow) = chosenPerColumn(NodeLine(style = NodeLineStyle.SHARP_LINEAR)).ends()

        assertTrue(bezierNarrow * 2 > bezierWide, "a stroke is as wide at both ends: $bezierWide, $bezierNarrow")
        assertTrue(sharpWide >= 14, "a sharp line is the line's width at one end: $sharpWide")
        assertTrue(sharpNarrow * 3 < sharpWide, "and narrows to the other: $sharpWide, $sharpNarrow")
    }
}
