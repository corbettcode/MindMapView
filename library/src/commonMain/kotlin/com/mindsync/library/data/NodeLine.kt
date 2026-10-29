package com.mindsync.library.data

import androidx.compose.ui.graphics.Color
import com.mindsync.library.util.Dp

/** How the line to a node is drawn, with the names FreeMind gives them in a map file. */
enum class NodeLineStyle(val freeMindName: String) {
    LINEAR("linear")
    , BEZIER("bezier")
    , SHARP_LINEAR("sharp_linear")
    , SHARP_BEZIER("sharp_bezier")
}

/** The widths of a line to choose from, as FreeMind has them. */
enum class NodeLineWidth(
    val label: String
    , val width: Dp
) {
    THIN("thin", Dp(0.75f))
    , ONE("1", Dp(1f))
    , TWO("2", Dp(2f))
    , FOUR("4", Dp(4f))
    , EIGHT("8", Dp(8f))
}

/** The line settings a node has of its own; what is null is taken from the parent. */
data class NodeLine(
    val style: NodeLineStyle? = null
    , val color: Color? = null
    , val width: NodeLineWidth? = null
) {
    val isEmpty: Boolean
        get() = style == null && color == null && width == null
}
