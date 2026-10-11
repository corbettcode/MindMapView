package com.mindsync.library.node

import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.toPx

class NodeDrawerFactory(
    private val node: NodeData<*>
    , private val drawInfo: DrawInfo
    , private val density: Density
    , private val depth: Int = 0
) {
    fun createStrokeNode(): NodeDrawer {
        val stroke = Stroke(width = drawInfo.strokeWidth.toPx(density))
        return when(node) {
            is RectangleNodeData -> {
                RectangleNodeDrawer(
                    node,
                    drawInfo,
                    drawInfo.strokeColor,
                    stroke,
                )
            }
            is StadiumNodeData -> {
                StadiumNodeDrawer(
                    node,
                    drawInfo,
                    drawInfo.strokeColor,
                    stroke,
                )
            }
            is CircleNodeData -> {
                CircleNodeDrawer(
                    node
                    , drawInfo = drawInfo
                    , color = drawInfo.strokeColor
                    , style = stroke
                )
            }
        }
    }

    fun createNodeDrawer(): NodeDrawer {
        return when (node) {
            is RectangleNodeData -> {
                RectangleNodeDrawer(
                    node,
                    drawInfo,
                    drawInfo.nodeColors[(depth - 1).mod(drawInfo.nodeColors.size)],
                    Fill,
                )
            }

            is StadiumNodeData -> {
                StadiumNodeDrawer(
                    node,
                    drawInfo,
                    drawInfo.nodeColors[(depth - 1).mod(drawInfo.nodeColors.size)],
                    Fill,
                )
            }

            is CircleNodeData -> {
                CircleNodeDrawer(
                    node,
                    drawInfo,
                    drawInfo.circleColor,
                    Fill,
                )
            }
        }
    }
}
