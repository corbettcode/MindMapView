package com.mindsync.library.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.util.toPx

class OvalNodeDrawer(
    private val node: OvalNodeData,
    private val color: Color,
    private val style: DrawStyle,
) : NodeDrawer {
    override fun drawNode(drawScope: DrawScope) {
        drawScope.drawOval(
            color = color,
            topLeft = Offset(node.path.leftX().toPx(drawScope), node.path.topY().toPx(drawScope)),
            size = Size(node.path.width().toPx(drawScope), node.path.height().toPx(drawScope)),
            style = style,
        )
    }

    override fun drawText(drawScope: DrawScope, text: TextLayoutResult) {
        drawScope.drawText(
            textLayoutResult = text,
            color = node.textColor,
            topLeft = Offset(
                node.path.centerX.toPx(drawScope) - text.size.width / 2f,
                node.path.centerY.toPx(drawScope) - text.size.height / 2f,
            ),
        )
    }
}
