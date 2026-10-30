package com.mindsync.library.node

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx

class NodeDrawerFactory(
    private val node: NodeData<*>
    , private val drawInfo: DrawInfo
    , private val density: Density
    // The color and width of the line to the node's parent, which a line node is drawn in.
    , private val lineColor: Color? = null
    , private val lineWidth: Dp? = null
) {
    fun createStrokeNode(): NodeDrawer {
        val stroke = Stroke(width = drawInfo.strokeWidth.toPx(density))
        return when(node) {
            is RectangleNodeData -> RectangleNodeDrawer(node.path, node.textColor, drawInfo.strokeColor, stroke)
            is SquareNodeData -> RectangleNodeDrawer(node.path, node.textColor, drawInfo.strokeColor, stroke)
            // A selected line node is outlined as a box, as the line alone would be hard to tell apart.
            is LineNodeData -> RectangleNodeDrawer(node.path, node.textColor, drawInfo.strokeColor, stroke)
            is StadiumNodeData -> {
                StadiumNodeDrawer(
                    node,
                    drawInfo.strokeColor,
                    stroke,
                )
            }
            is OvalNodeData -> {
                OvalNodeDrawer(
                    node,
                    drawInfo.strokeColor,
                    stroke,
                )
            }
            is CircleNodeData -> {
                CircleNodeDrawer(
                    node
                    , color = drawInfo.strokeColor
                    , style = stroke
                )
            }
        }
    }

    fun createNodeDrawer(): NodeDrawer {
        return when (node) {
            is RectangleNodeData -> RectangleNodeDrawer(node.path, node.textColor, node.color, Fill)
            is SquareNodeData -> RectangleNodeDrawer(node.path, node.textColor, node.color, Fill)
            is LineNodeData -> LineNodeDrawer(
                node.path
                , node.textColor
                , lineColor ?: node.color
                , lineWidth ?: Dp(DEFAULT_LINE_NODE_WIDTH)
            )

            is StadiumNodeData -> {
                StadiumNodeDrawer(
                    node,
                    node.color,
                    Fill,
                )
            }

            is OvalNodeData -> {
                OvalNodeDrawer(
                    node,
                    node.color,
                    Fill,
                )
            }

            is CircleNodeData -> {
                CircleNodeDrawer(
                    node,
                    node.color,
                    Fill,
                )
            }
        }
    }
}

private const val DEFAULT_LINE_NODE_WIDTH = 2f
