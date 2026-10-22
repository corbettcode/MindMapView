package com.mindsync.library.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * The side of a node its children are placed on.
 *
 * The root has a set of children for each of the six sides: one straight out to its left and right, and one
 * above and below each of those, out along a line [angleDegrees] off the horizontal. The nodes below a child of
 * the root grow out to the left or right of it, its [horizontal] side, as on any other node.
 */
enum class LayoutSide(val angleDegrees: Float) {
    RIGHT(0f),
    LEFT(180f),
    UPPER_RIGHT(SLANT_DEGREES),
    UPPER_LEFT(180f - SLANT_DEGREES),
    LOWER_RIGHT(-SLANT_DEGREES),
    LOWER_LEFT(SLANT_DEGREES - 180f);

    val isLeft: Boolean get() = this == LEFT || this == UPPER_LEFT || this == LOWER_LEFT

    /** Whether the children on this side of the root are above or below it, rather than straight out of it. */
    val isSlanted: Boolean get() = this != LEFT && this != RIGHT

    val isUpper: Boolean get() = this == UPPER_LEFT || this == UPPER_RIGHT

    /** [LEFT] or [RIGHT]: the way the nodes on this side grow out of their parent. */
    val horizontal: LayoutSide get() = if (isLeft) LEFT else RIGHT

    /** The side across from this one, at the same height. */
    val mirrored: LayoutSide
        get() = when (this) {
            RIGHT -> LEFT
            LEFT -> RIGHT
            UPPER_RIGHT -> UPPER_LEFT
            UPPER_LEFT -> UPPER_RIGHT
            LOWER_RIGHT -> LOWER_LEFT
            LOWER_LEFT -> LOWER_RIGHT
        }

    /**
     * The next side round the root, as its buttons are: clockwise on the screen, from the right side down to
     * the lower right, or counterclockwise, from the right side up to the upper right.
     */
    fun next(isClockwise: Boolean): LayoutSide {
        val index = COUNTERCLOCKWISE.indexOf(this) + if (isClockwise) -1 else 1
        return COUNTERCLOCKWISE[(index + COUNTERCLOCKWISE.size) % COUNTERCLOCKWISE.size]
    }

    companion object {
        private val COUNTERCLOCKWISE by lazy {
            listOf(RIGHT, UPPER_RIGHT, UPPER_LEFT, LEFT, LOWER_LEFT, LOWER_RIGHT)
        }

        /** The side of the root whose line out of it is nearest to the direction ([dx], [dy]), with y down. */
        fun nearest(
            dx: Float
            , dy: Float
        ): LayoutSide {
            val degrees = atan2(-dy, dx) * 180f / PI.toFloat()
            return entries.minBy { side ->
                val difference = abs(degrees - side.angleDegrees) % 360f
                if (difference > 180f) 360f - difference else difference
            }
        }
    }
}

// The six sides are evenly spaced around the root.
private const val SLANT_DEGREES = 60f
