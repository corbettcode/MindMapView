package com.mindsync.library.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.halfHeight
import com.mindsync.library.halfWidth
import com.mindsync.library.util.toPx

/** How far the bracket of a summary is past the furthest out of the nodes it is about. */
internal val SUMMARY_BRACKET_GAP = 8.dp

/** How wide the bracket of a summary is, from its back to its point. */
internal val SUMMARY_BRACKET_WIDTH = 12.dp

/** How far a summary node is past the point of its bracket. */
internal val SUMMARY_SPACE = 24.dp

/** How far a summary node is past the furthest out of the nodes it is about: its bracket is in between. */
internal val SUMMARY_OFFSET = SUMMARY_BRACKET_GAP + SUMMARY_BRACKET_WIDTH + SUMMARY_SPACE

/**
 * Where the bracket of a summary is, in the pixels of the tree, along the way its branch grows ("x") and across it
 * ("y"), which are the screen's x and y when it [isHorizontal], and turned when it grows down or up: along [backX]
 * from [top] to [bottom], the first to the last across of the shown nodes it is about, with its point at ([pointX],
 * [pointY]), and a line from there to the summary node at [nodeX].
 */
internal class SummaryBracket(
    val top: Float
    , val bottom: Float
    , val backX: Float
    , val pointX: Float
    , val pointY: Float
    , val nodeX: Float
    , val isHorizontal: Boolean = true
) {
    /** The point of the screen [along] the way the branch grows and [across] it. */
    fun at(
        along: Float
        , across: Float
    ): Offset = if (isHorizontal) Offset(along, across) else Offset(across, along)
}

/**
 * Returns the bracket of the summary [summaryId], past the furthest out of the shown nodes it is about and the nodes
 * below them, on its side of the root. Returns null when it is no summary or is not shown.
 */
internal fun Density.summaryBracket(
    tree: Tree<*>
    , summaryId: String
): SummaryBracket? {
    if (!tree.isVisible(summaryId)) return null
    val rangeIds = tree.summaryChildIds(summaryId).takeIf { it.isNotEmpty() } ?: return null
    val grow = tree.summaryDirectionOf(summaryId)
    val isHorizontal = grow.isHorizontal
    val isBack = grow.sign < 0f
    // Along the way the branch grows, and across it, in pixels.
    fun along(node: NodeData<*>) = (if (isHorizontal) node.path.centerX else node.path.centerY).toPx(this)
    fun across(node: NodeData<*>) = (if (isHorizontal) node.path.centerY else node.path.centerX).toPx(this)
    fun halfAlong(node: NodeData<*>) = (if (isHorizontal) halfWidth(node) else halfHeight(node)).toPx(this)
    fun halfAcross(node: NodeData<*>) = (if (isHorizontal) halfHeight(node) else halfWidth(node)).toPx(this)
    var top = Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    var edge = if (isBack) Float.MAX_VALUE else -Float.MAX_VALUE
    rangeIds.forEach { id ->
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            top = minOf(top, across(node) - halfAcross(node))
            bottom = maxOf(bottom, across(node) + halfAcross(node))
            edge = if (isBack) minOf(edge, along(node) - halfAlong(node))
                else maxOf(edge, along(node) + halfAlong(node))
        }
    }
    val summary = tree.getNode(summaryId)
    val sign = if (isBack) -1f else 1f
    val backX = edge + sign * SUMMARY_BRACKET_GAP.toPx()
    val pointX = backX + sign * SUMMARY_BRACKET_WIDTH.toPx()
    val nodeX = along(summary) - sign * halfAlong(summary)
    // The point faces the summary node, which is centered on the siblings, but is kept off the bracket's ends.
    val end = minOf(SUMMARY_BRACKET_WIDTH.toPx(), (bottom - top) / 2)
    val pointY = across(summary).coerceIn(top + end, bottom - end)
    return SummaryBracket(top, bottom, backX, pointX, pointY, nodeX, isHorizontal)
}

/**
 * Returns the children a brace of a brace map joins [parent] to: its shown children but summaries and floating
 * topics.
 */
internal fun braceChildIds(
    tree: Tree<*>
    , parent: NodeData<*>
): List<String> = tree.visibleChildren(parent).filterNot { tree.isSummary(it) || tree.isFloating(it) }

/**
 * Returns the brace of a brace map from [parent] to its children: along their near side, from the top of the first to
 * the bottom of the last, with its point facing the parent and a line from there to the parent's edge. Returns null
 * for a topic without children shown.
 */
internal fun Density.braceOf(
    tree: Tree<*>
    , parent: NodeData<*>
): SummaryBracket? {
    val childIds = braceChildIds(tree, parent).takeIf { it.isNotEmpty() } ?: return null
    val children = childIds.map { tree.getNode(it) }
    val top = children.minOf { it.path.centerY.toPx(this) - halfHeight(it).toPx(this) }
    val bottom = children.maxOf { it.path.centerY.toPx(this) + halfHeight(it).toPx(this) }
    val near = children.minOf { it.path.centerX.toPx(this) - halfWidth(it).toPx(this) }
    val backX = near - SUMMARY_BRACKET_GAP.toPx()
    val pointX = backX - SUMMARY_BRACKET_WIDTH.toPx()
    val nodeX = parent.path.centerX.toPx(this) + halfWidth(parent).toPx(this)
    val end = minOf(SUMMARY_BRACKET_WIDTH.toPx(), (bottom - top) / 2)
    val pointY = parent.path.centerY.toPx(this).coerceIn(top + end, bottom - end)
    return SummaryBracket(top, bottom, backX, pointX, pointY, nodeX)
}
