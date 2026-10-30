package com.mindsync.library.data

import androidx.compose.ui.graphics.Color

/**
 * The shapes a node can take, each with the colors a node of that shape gets unless it is given its own.
 */
enum class NodeShape(
    val defaultColor: Color
    , val defaultTextColor: Color
) {
    CIRCLE(Color(0xFF2D5728), Color.White)
    , RECTANGLE(Color(0xFF8E7CC3), Color.Black)
    , STADIUM(Color(0xFFB9BC81), Color.Black)
    , OVAL(Color(0xFF9BC4E2), Color.Black)
    , SQUARE(Color(0xFFC9B6E4), Color.Black)
    // A line node has no fill, so its text is in a color that reads on a light background and a dark one alike.
    , LINE(Color(0xFF3D7CC9), Color(0xFF3D7CC9))
}

val NodeData<*>.shape: NodeShape
    get() = when (this) {
        is CircleNodeData -> NodeShape.CIRCLE
        is RectangleNodeData -> NodeShape.RECTANGLE
        is StadiumNodeData -> NodeShape.STADIUM
        is OvalNodeData -> NodeShape.OVAL
        is SquareNodeData -> NodeShape.SQUARE
        is LineNodeData -> NodeShape.LINE
    }
