package com.corbettcode.mindmapview.library.node

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import com.corbettcode.mindmapview.library.data.CircleNodeData
import com.corbettcode.mindmapview.library.data.LineNodeData
import com.corbettcode.mindmapview.library.data.NodeBorder
import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.OvalNodeData
import com.corbettcode.mindmapview.library.data.RectangleNodeData
import com.corbettcode.mindmapview.library.data.SquareNodeData
import com.corbettcode.mindmapview.library.data.StadiumNodeData
import com.corbettcode.mindmapview.library.model.DrawInfo
import com.corbettcode.mindmapview.library.util.Dp
import com.corbettcode.mindmapview.library.util.toPx

class NodeDrawerFactory(
    private val node: NodeData<*>
    , private val drawInfo: DrawInfo
    , private val density: Density
    // The color and width of the line to the node's parent, which a line node is drawn in.
    , private val lineColor: Color? = null
    , private val lineWidth: Dp? = null
) {
    /** The outline that shows the node is selected. */
    fun createStrokeNode(): NodeDrawer = outline(drawInfo.strokeColor, drawInfo.strokeWidth)

    /** The node's [border]. */
    fun createBorderDrawer(border: NodeBorder): NodeDrawer = outline(border.color, border.width.width)

    // A line node is outlined as a box, as an outline of the line alone would be hard to tell apart from it.
    private fun outline(
        color: Color
        , width: Dp
    ): NodeDrawer {
        val stroke = Stroke(width = width.toPx(density))
        return when (node) {
            is RectangleNodeData -> RectangleNodeDrawer(node.path, node.textColor, color, stroke)
            is SquareNodeData -> RectangleNodeDrawer(node.path, node.textColor, color, stroke)
            is LineNodeData -> RectangleNodeDrawer(node.path, node.textColor, color, stroke)
            is StadiumNodeData -> StadiumNodeDrawer(node, color, stroke)
            is OvalNodeData -> OvalNodeDrawer(node, color, stroke)
            is CircleNodeData -> CircleNodeDrawer(node, color, stroke)
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
