package com.mindsync.library.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Boundary
import com.mindsync.library.data.Tree
import com.mindsync.library.halfHeight
import com.mindsync.library.halfWidth
import com.mindsync.library.util.toPx

/** How far a boundary is outside the nodes it is around, and outside the boundaries within it. */
internal val BOUNDARY_PADDING = 10.dp

/**
 * Returns the rectangle the boundary is drawn around, in the pixels of the tree: around the nodes that are shown of
 * its children and the nodes below them, and around the boundaries within those, with [BOUNDARY_PADDING] all
 * round. Returns null when none of its children are shown, as when its parent is folded or hidden in a folded node.
 */
internal fun Density.boundaryRect(
    tree: Tree<*>
    , boundary: Boundary
): Rect? = boundaryRect(tree, boundary, tree.boundaries)

private fun Density.boundaryRect(
    tree: Tree<*>
    , boundary: Boundary
    , boundaries: List<Boundary>
): Rect? {
    val parent = tree.nodes[boundary.parentId] ?: return null
    if (!tree.isVisible(parent.id) || tree.isFolded(parent.id)) return null
    var rect: Rect? = null
    fun add(other: Rect) {
        rect = rect?.union(other) ?: other
    }
    val coveredIds = mutableSetOf<String>()
    tree.boundaryChildIds(boundary).forEach { childId ->
        tree.doVisiblePreorderTraversal(tree.getNode(childId)) { node ->
            coveredIds.add(node.id)
            val center = Offset(node.path.centerX.toPx(this), node.path.centerY.toPx(this))
            val half = Offset(halfWidth(node).toPx(this), halfHeight(node).toPx(this))
            add(Rect(center - half, center + half))
        }
    }
    // A boundary within is one around children of a node that is inside this one; it is further down the tree, so
    // the nesting ends.
    boundaries
        .filter { it.parentId in coveredIds }
        .forEach { inner -> boundaryRect(tree, inner, boundaries)?.let(::add) }
    return rect?.inflate(BOUNDARY_PADDING.toPx())
}

/**
 * Returns the boundaries that are drawn, with the rectangle of each, in the order they are drawn in: each one
 * before those within it, so those are on top of it.
 */
internal fun Density.drawnBoundaries(tree: Tree<*>): List<Pair<Boundary, Rect>> = tree.boundaries
    .sortedBy { tree.levelOf(it.parentId) }
    .mapNotNull { boundary -> boundaryRect(tree, boundary)?.let { boundary to it } }

private fun Rect.union(other: Rect): Rect = Rect(
    minOf(left, other.left)
    , minOf(top, other.top)
    , maxOf(right, other.right)
    , maxOf(bottom, other.bottom)
)
