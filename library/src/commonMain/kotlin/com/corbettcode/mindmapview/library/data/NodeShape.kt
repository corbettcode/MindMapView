package com.corbettcode.mindmapview.library.data

import androidx.compose.ui.graphics.Color

/**
 * The shapes a node can take, each with the colors a node of that shape gets unless it is given its own.
 *
 * Defines the supported visual shapes for a node.
 *
 * Each shape provides default styling intended for rendering a node consistently
 * across diagrams or graph-based views. Filled shapes use a default fill color
 * and a contrasting text color, while line-based nodes use a color suitable for
 * both the line and its text.
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
    ;

    /** Whether a node of this shape can be drawn raised off the map, with a shadow below it: see [Tree.isElevated]. */
    val canBeElevated: Boolean
        get() = this == RECTANGLE || this == STADIUM
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
