package com.mindsync.library.data

import androidx.compose.ui.graphics.Color
import com.mindsync.library.util.Dp

/**
 * An arrow from one node to another, drawn as a curve on top of the tree, as FreeMind's arrow links are.
 * It is not part of the structure of the tree: its nodes may be anywhere in it.
 *
 * The curve leaves the node with id [sourceId] towards [startInclination] and arrives at the node with id
 * [targetId] from [endInclination]. An inclination that is null is chosen from where the two nodes are.
 */
data class ArrowLink(
    val id: String
    , val sourceId: String
    , val targetId: String
    , val color: Color = DEFAULT_COLOR
    , val hasStartArrow: Boolean = false
    , val hasEndArrow: Boolean = true
    , val startInclination: ArrowLinkInclination? = null
    , val endInclination: ArrowLinkInclination? = null
) {
    companion object {
        val DEFAULT_COLOR = Color(0xFFB0B0B0)
    }
}

/**
 * Where the control point of an end of an [ArrowLink] is, from that end. An end is on the right edge of
 * its node unless [x] is negative.
 */
data class ArrowLinkInclination(
    val x: Dp
    , val y: Dp
)
