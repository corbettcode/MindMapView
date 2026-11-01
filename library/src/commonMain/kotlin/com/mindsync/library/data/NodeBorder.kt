package com.mindsync.library.data

import androidx.compose.ui.graphics.Color

/** The kinds of border a node can have. */
enum class NodeBorderStyle {
    NONE
    , LINE
}

/**
 * What is drawn around a node: nothing, or a line [width] wide in [color]. A border without a color of its own
 * is drawn in the color of the node's text, so it changes along with it.
 */
data class NodeBorder(
    val style: NodeBorderStyle = NodeBorderStyle.NONE
    , val width: NodeLineWidth = NodeLineWidth.TWO
    , val color: Color? = null
) {
    val isNone: Boolean
        get() = style == NodeBorderStyle.NONE
}
