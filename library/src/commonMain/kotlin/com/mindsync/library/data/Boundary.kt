package com.mindsync.library.data

import androidx.compose.ui.graphics.Color

/**
 * An outline drawn around some of the children of one node, and all of the nodes below them, as XMind's
 * boundaries and FreeMind's clouds are. It groups them, and is not part of the structure of the tree.
 *
 * It is around the children of the node with id [parentId] from the first to the last of [childIds], in the
 * order of the children, so a child added between them is inside it too. Under the root they are the children
 * on one side of it. A child that is no longer one of the parent's, or not on that side, is left out, and a
 * boundary with none of its children left is no longer in the tree.
 *
 * It is drawn in [color], or where that is null in the color of the line from the root to its branch, with a
 * line in [style] and, when [isFilled], a light fill of the same color.
 */
data class Boundary(
    val id: String
    , val parentId: String
    , val childIds: Set<String>
    , val color: Color? = null
    , val style: BoundaryStyle = BoundaryStyle.DASHED
    , val isFilled: Boolean = true
)

/** How the outline of a [Boundary] is stroked. */
enum class BoundaryStyle {
    SOLID
    , DASHED
}
