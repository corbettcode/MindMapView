package com.corbettcode.mindmapview.library.data

import com.corbettcode.mindmapview.library.util.Dp

/** How far one point is from another, [x] to the right and [y] down, in dp. */
data class DpOffset(
    val x: Dp
    , val y: Dp
) {
    operator fun plus(other: DpOffset): DpOffset = DpOffset(x + other.x, y + other.y)

    companion object {
        val ZERO = DpOffset(Dp(0f), Dp(0f))
    }
}
