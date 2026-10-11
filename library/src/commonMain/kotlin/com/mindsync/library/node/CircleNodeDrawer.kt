package com.mindsync.library.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.toPx

class CircleNodeDrawer(
    private val node: CircleNodeData
    , private val drawInfo: DrawInfo
    , private val color: Color
    , private val style: DrawStyle
) : NodeDrawer {
    override fun drawNode(drawScope: DrawScope) {
        drawScope.drawCircle(
            color = color
            , radius = node.path.radius.toPx(drawScope)
            , center = Offset(node.path.centerX.toPx(drawScope), node.path.centerY.toPx(drawScope))
            , style = style
        )
    }

    override fun drawText(drawScope: DrawScope, text: TextLayoutResult) {
        drawScope.drawText(
            textLayoutResult = text
            , color = drawInfo.circleTextColor
            , topLeft = Offset(
                node.path.centerX.toPx(drawScope) - text.size.width / 2f
                , node.path.centerY.toPx(drawScope) - text.size.height / 2f
            )
        )
    }
}
