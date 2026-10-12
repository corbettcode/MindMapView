package com.mindsync.library.node

import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.util.toPx

class NodeDrawerFactory(
    private val node: NodeData<*>
    , private val drawInfo: DrawInfo
    , private val density: Density
) {
    fun createStrokeNode(): NodeDrawer {
        val stroke = Stroke(width = drawInfo.strokeWidth.toPx(density))
        return when(node) {
            is RectangleNodeData -> {
                RectangleNodeDrawer(
                    node,
                    drawInfo.strokeColor,
                    stroke,
                )
            }
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
            is RectangleNodeData -> {
                RectangleNodeDrawer(
                    node,
                    node.color,
                    Fill,
                )
            }

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
