package com.mindsync.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Room around the nodes for what is drawn beside them: fold markers, note markers and arrowheads.
private val IMAGE_MARGIN = Dp(24f)

/**
 * Returns the mind map drawn in an image of its own, whatever part of it is in view, at [pixelsPerDp] or smaller
 * when its largest side would be more than [maxSide] pixels. It is drawn in the colors of a light theme on white,
 * to be put in a document. The text is drawn as it was measured, in [density], and scaled with the rest.
 */
internal fun mapImage(
    state: MindMapState
    , density: Density
    , measureTextSize: MeasureTextSize
    , pixelsPerDp: Float
    , maxSide: Int
): ImageBitmap {
    val bounds = density.mapBounds(state.tree)
    val scale = min(pixelsPerDp / density.density, maxSide / max(bounds.width, bounds.height))
    val width = (bounds.width * scale).roundToInt().coerceAtLeast(1)
    val height = (bounds.height * scale).roundToInt().coerceAtLeast(1)
    val image = ImageBitmap(width, height)
    val drawInfo = DrawInfo()
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        drawRect(Color.White)
        scale(scale, Offset.Zero) {
            translate(-bounds.left, -bounds.top) {
                drawMap(state, drawInfo, measureTextSize, drawInfo.lineColor)
            }
        }
    }
    return image
}

// What the nodes that are shown and their arrow links take up, in the pixels of the tree. A curve stays within
// its control points, so they are what an arrow link takes up at most.
private fun Density.mapBounds(tree: Tree<*>): Rect {
    var bounds = Rect.Zero
    var isEmpty = true
    fun add(rect: Rect) {
        bounds = if (isEmpty) rect else bounds.union(rect)
        isEmpty = false
    }
    tree.doVisiblePreorderTraversal { node ->
        val center = Offset(node.path.centerX.toPx(this), node.path.centerY.toPx(this))
        val half = Offset(halfWidth(node).toPx(this), halfHeight(node).toPx(this))
        add(Rect(center - half, center + half))
    }
    tree.arrowLinks.forEach { link ->
        val curve = arrowLinkCurve(tree, link) ?: return@forEach
        listOf(curve.start, curve.startControl, curve.endControl, curve.end).forEach { add(Rect(it, it)) }
    }
    return bounds.inflate(IMAGE_MARGIN.toPx(this))
}

private fun Rect.union(other: Rect): Rect =
    Rect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
