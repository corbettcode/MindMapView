package com.mindsync.library.layout

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
 * Where the bracket of a summary is, in the pixels of the tree: along [backX] from [top] to [bottom], the top to the
 * bottom of the shown nodes it is about, with its point at ([pointX], [pointY]), and a line from there to the summary
 * node at [nodeX].
 */
internal class SummaryBracket(
    val top: Float
    , val bottom: Float
    , val backX: Float
    , val pointX: Float
    , val pointY: Float
    , val nodeX: Float
)

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
    val isLeft = tree.sideOf(summaryId).isLeft
    var top = Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    var edge = if (isLeft) Float.MAX_VALUE else -Float.MAX_VALUE
    rangeIds.forEach { id ->
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            val centerX = node.path.centerX.toPx(this)
            val centerY = node.path.centerY.toPx(this)
            val halfWidth = halfWidth(node).toPx(this)
            val halfHeight = halfHeight(node).toPx(this)
            top = minOf(top, centerY - halfHeight)
            bottom = maxOf(bottom, centerY + halfHeight)
            edge = if (isLeft) minOf(edge, centerX - halfWidth) else maxOf(edge, centerX + halfWidth)
        }
    }
    val summary = tree.getNode(summaryId)
    val direction = if (isLeft) -1f else 1f
    val backX = edge + direction * SUMMARY_BRACKET_GAP.toPx()
    val pointX = backX + direction * SUMMARY_BRACKET_WIDTH.toPx()
    val nodeX = summary.path.centerX.toPx(this) - direction * halfWidth(summary).toPx(this)
    // The point faces the summary node, which is centered on the siblings, but is kept off the bracket's ends.
    val end = minOf(SUMMARY_BRACKET_WIDTH.toPx(), (bottom - top) / 2)
    val pointY = summary.path.centerY.toPx(this).coerceIn(top + end, bottom - end)
    return SummaryBracket(top, bottom, backX, pointX, pointY, nodeX)
}
