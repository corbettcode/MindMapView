package com.mindsync.library.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.mindsync.library.data.RectanglePath
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx

/** Draws a node as a line along the bottom of its box, in [color] and [width], with its text above the line. */
class LineNodeDrawer(
    private val path: RectanglePath
    , private val textColor: Color
    , private val color: Color
    , private val width: Dp
) : NodeDrawer {
    override fun drawNode(drawScope: DrawScope) {
        val y = path.bottomY().toPx(drawScope)
        drawScope.drawLine(
            color = color
            , start = Offset(path.leftX().toPx(drawScope), y)
            , end = Offset(path.rightX().toPx(drawScope), y)
            , strokeWidth = width.toPx(drawScope)
            , cap = StrokeCap.Round
        )
    }

    // The box leaves little room around the text, so the text centered in it sits just above the line.
    override fun drawText(drawScope: DrawScope, text: TextLayoutResult) {
        drawScope.drawText(
            textLayoutResult = text
            , color = textColor
            , topLeft = Offset(
                path.centerX.toPx(drawScope) - text.size.width / 2f
                , path.centerY.toPx(drawScope) - text.size.height / 2f
            )
        )
    }
}
