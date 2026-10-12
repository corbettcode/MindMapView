package com.mindsync.library.data

import androidx.compose.ui.graphics.Color

/**
 * The shapes a node can take, each with the colors a node of that shape gets unless it is given its own.
 */
enum class NodeShape(
    val defaultColor: Color
    , val defaultTextColor: Color
) {
    CIRCLE(Color(0xFFE1719F), Color.White),
    RECTANGLE(Color(0xFFFFCF96), Color.Black),
    STADIUM(Color(0xFFB9BC81), Color.Black),
}

val NodeData<*>.shape: NodeShape
    get() = when (this) {
        is CircleNodeData -> NodeShape.CIRCLE
        is RectangleNodeData -> NodeShape.RECTANGLE
        is StadiumNodeData -> NodeShape.STADIUM
    }
