package com.corbettcode.mindmapview.library.node

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.corbettcode.mindmapview.library.data.RectanglePath
import com.corbettcode.mindmapview.library.util.Dp
import com.corbettcode.mindmapview.library.util.toPx

class RectangleNodeDrawer(
    private val path: RectanglePath,
    private val textColor: Color,
    private val color: Color,
    private val style: DrawStyle,
) : NodeDrawer {
    override fun drawNode(drawScope: DrawScope) {
        drawScope.drawRoundRect(
            color = color,
            topLeft = Offset(path.leftX().toPx(drawScope), path.topY().toPx(drawScope)),
            size = Size(path.width.toPx(drawScope), path.height.toPx(drawScope)),
            cornerRadius = CornerRadius(Dp(ROUNDED_CORNER_RADIUS).toPx(drawScope)),
            style = style,
        )
    }

    override fun drawText(drawScope: DrawScope, text: TextLayoutResult) {
        drawScope.drawText(
            textLayoutResult = text,
            color = textColor,
            topLeft = Offset(
                path.centerX.toPx(drawScope) - text.size.width / 2f,
                path.centerY.toPx(drawScope) - text.size.height / 2f,
            ),
        )
    }

    companion object {
        internal const val ROUNDED_CORNER_RADIUS = 8f
    }
}

/**
 * Draws the shadow of a rectangle raised off the map at [path], to go below it: a little lower than the rectangle,
 * with an edge that fades out. A blur is not drawn the same on every platform, nor kept in an SVG, so the edge is
 * layers of faint rectangles, each a little wider, that are darker where more of them lie on one another.
 */
internal fun DrawScope.drawElevationShadow(path: RectanglePath) {
    val left = path.leftX().toPx(this)
    val top = path.topY().toPx(this) + Dp(SHADOW_DROP).toPx(this)
    val width = path.width.toPx(this)
    val height = path.height.toPx(this)
    val radius = Dp(RectangleNodeDrawer.ROUNDED_CORNER_RADIUS).toPx(this)
    val reach = Dp(SHADOW_REACH).toPx(this)
    val layerColor = SHADOW_COLOR.copy(alpha = SHADOW_ALPHA / SHADOW_LAYERS)
    for (layer in 1..SHADOW_LAYERS) {
        val spread = reach * layer / SHADOW_LAYERS
        drawRoundRect(
            color = layerColor
            , topLeft = Offset(left - spread, top - spread)
            , size = Size(width + spread * 2, height + spread * 2)
            , cornerRadius = CornerRadius(radius + spread)
        )
    }
}

// How far below the rectangle its shadow is, and how far past its edges the shadow fades out, in dp.
private const val SHADOW_DROP = 4f
private const val SHADOW_REACH = 5f
private const val SHADOW_LAYERS = 16
// How dark the shadow is under the rectangle, where all its layers lie on one another.
private const val SHADOW_ALPHA = 0.3f
private val SHADOW_COLOR = Color.Black
