package com.corbettcode.mindmapview.library.node

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.corbettcode.mindmapview.library.data.RectanglePath
import com.corbettcode.mindmapview.library.data.StadiumPath
import com.corbettcode.mindmapview.library.util.Dp
import com.corbettcode.mindmapview.library.util.toPx

/** Draws the shadow of a rectangle raised off the map at [path], to go below it. */
internal fun DrawScope.drawElevationShadow(path: RectanglePath) = drawElevationShadow(
    left = path.leftX()
    , top = path.topY()
    , width = path.width
    , height = path.height
    , cornerRadius = Dp(RectangleNodeDrawer.ROUNDED_CORNER_RADIUS)
)

/** Draws the shadow of a stadium raised off the map at [path], to go below it, with ends as round as its own. */
internal fun DrawScope.drawElevationShadow(path: StadiumPath) = drawElevationShadow(
    left = path.leftX()
    , top = path.topY()
    , width = path.width()
    , height = path.height()
    , cornerRadius = path.radius
)

/**
 * Draws the shadow of a rounded box raised off the map: a little lower than the box, with an edge that fades out. A
 * blur is not drawn the same on every platform, nor kept in an SVG, so the edge is layers of faint boxes, each a
 * little wider, that are darker where more of them lie on one another.
 */
private fun DrawScope.drawElevationShadow(
    left: Dp
    , top: Dp
    , width: Dp
    , height: Dp
    , cornerRadius: Dp
) {
    val leftPx = left.toPx(this)
    val topPx = top.toPx(this) + Dp(SHADOW_DROP).toPx(this)
    val widthPx = width.toPx(this)
    val heightPx = height.toPx(this)
    val radius = cornerRadius.toPx(this)
    val reach = Dp(SHADOW_REACH).toPx(this)
    val layerColor = SHADOW_COLOR.copy(alpha = SHADOW_ALPHA / SHADOW_LAYERS)
    for (layer in 1..SHADOW_LAYERS) {
        val spread = reach * layer / SHADOW_LAYERS
        drawRoundRect(
            color = layerColor
            , topLeft = Offset(leftPx - spread, topPx - spread)
            , size = Size(widthPx + spread * 2, heightPx + spread * 2)
            , cornerRadius = CornerRadius(radius + spread)
        )
    }
}

// How far below the box its shadow is, and how far past its edges the shadow fades out, in dp.
private const val SHADOW_DROP = 4f
private const val SHADOW_REACH = 5f
private const val SHADOW_LAYERS = 16
// How dark the shadow is under the box, where all its layers lie on one another.
private const val SHADOW_ALPHA = 0.3f
private val SHADOW_COLOR = Color.Black
