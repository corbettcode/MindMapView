package com.mindsync.library.data

import androidx.compose.ui.graphics.Color

/** The kinds of border a node can have. */
enum class NodeBorderStyle {
    NONE
    , LINE
}

/**
 * What is drawn around a node: nothing, or a line [width] wide in [color]. Its color is its own, apart from that
 * of the node's text.
 */
data class NodeBorder(
    val style: NodeBorderStyle = NodeBorderStyle.NONE
    , val width: NodeLineWidth = NodeLineWidth.TWO
    , val color: Color = DEFAULT_COLOR
) {
    val isNone: Boolean
        get() = style == NodeBorderStyle.NONE

    companion object {
        val DEFAULT_COLOR = Color.Black
    }
}
