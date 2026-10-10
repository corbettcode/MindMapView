package com.mindsync.library.node

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx

class RectangleNodeDrawer(
    private val node: RectangleNodeData,
    private val drawInfo: DrawInfo,
    private val color: Color,
    private val style: DrawStyle,
) : NodeDrawer {
    override fun drawNode(drawScope: DrawScope) {
        drawScope.drawRoundRect(
            color = color,
            topLeft = Offset(node.path.leftX().toPx(drawScope), node.path.topY().toPx(drawScope)),
            size = Size(node.path.width.toPx(drawScope), node.path.height.toPx(drawScope)),
            cornerRadius = CornerRadius(Dp(ROUNDED_CORNER_RADIUS).toPx(drawScope)),
            style = style,
        )
    }

    override fun drawText(drawScope: DrawScope, text: TextLayoutResult) {
        drawScope.drawText(
            textLayoutResult = text,
            color = drawInfo.rectangleTextColor,
            topLeft = Offset(
                node.path.centerX.toPx(drawScope) - text.size.width / 2f,
                node.path.centerY.toPx(drawScope) - text.size.height / 2f,
            ),
        )
    }

    companion object {
        private const val ROUNDED_CORNER_RADIUS = 8f
    }
}
