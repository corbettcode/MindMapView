package com.mindsync.library.data

import com.mindsync.library.util.Dp
import kotlin.math.abs

/**
 * Represents the path of a node in the tree.
 */
sealed class NodePath(
    open var centerX: Dp
    , open var centerY: Dp
) {
    abstract fun adjustPath(
        horizontalSpacing: Dp
        , totalHeight: Dp
    ): NodePath

    protected fun calculateNewCenterY(
        horizontalSpacing: Dp
        , totalHeight: Dp
    ): Dp {
        return centerY + totalHeight / 2 + horizontalSpacing
    }
}

/**
 * Represents the path of a rectangle node in the tree.
 */
data class RectanglePath(
    override var centerX: Dp
    , override var centerY: Dp
    , val width: Dp
    , val height: Dp
) : NodePath(
    centerX
    , centerY
) {
    fun leftX() = centerX - (width / (Dp(2f)))

    fun topY() = centerY - (height / (Dp(2f)))

    fun rightX() = centerX + (width / (Dp(2f)))

    fun bottomY() = centerY + (height / (Dp(2f)))

    override fun adjustPath(
        horizontalSpacing: Dp
        , totalHeight: Dp
    ): RectanglePath {
        return this.copy(centerY = calculateNewCenterY(horizontalSpacing, totalHeight))
    }
}

/**
 * Represents the path of a circle node in the tree.
 */
data class CirclePath(
    override var centerX: Dp
    , override var centerY: Dp
    , val radius: Dp
): NodePath(
    centerX
    , centerY
) {
    override fun adjustPath(
        horizontalSpacing: Dp
        , totalHeight: Dp
    ): CirclePath {
        return this.copy(centerY = totalHeight / 2 + horizontalSpacing)
    }
}

/**
 * Represents the path of a stadium node in the tree: a rectangle whose left and right ends are capped
 * with semicircles. See https://mathworld.wolfram.com/Stadium.html.
 *
 * @param length the length of the straight top and bottom sides (`a`)
 * @param radius the radius of the semicircular caps (`r`)
 */
data class StadiumPath(
    override var centerX: Dp
    , override var centerY: Dp
    , val length: Dp
    , val radius: Dp
) : NodePath(
    centerX
    , centerY
) {
    fun width() = length + radius * 2

    fun height() = radius * 2

    fun leftX() = centerX - (width() / 2)

    fun topY() = centerY - radius

    fun rightX() = centerX + (width() / 2)

    fun bottomY() = centerY + radius

    /**
     * A point is inside the stadium when it is within [radius] of the segment joining the cap centers.
     */
    fun contains(
        x: Dp
        , y: Dp
    ): Boolean {
        val dx = maxOf(abs(x.dpVal - centerX.dpVal) - length.dpVal / 2, 0f)
        val dy = y.dpVal - centerY.dpVal
        return dx * dx + dy * dy <= radius.dpVal * radius.dpVal
    }

    override fun adjustPath(
        horizontalSpacing: Dp
        , totalHeight: Dp
    ): StadiumPath {
        return this.copy(centerY = calculateNewCenterY(horizontalSpacing, totalHeight))
    }
}
