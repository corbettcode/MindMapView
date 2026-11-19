package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.withCenter
import com.mindsync.library.halfHeight
import com.mindsync.library.halfWidth
import com.mindsync.library.util.Dp

/** The space between the root and the first topic of a timeline, and between the branches along it. */
private val AXIS_GAP = Dp(40f)

/**
 * Lays a map out as a timeline: the root's children on a horizontal axis through the root, to its right in their
 * order, each with its branch growing up from it, by [up], or down, by [down], by turns. Each is as far along as it
 * needs to be clear of the topic before it on the axis and of the branch before it on its own side, so the branches
 * above and below interleave. A summary of the root's children is below them, centered on them.
 */
internal class TimelineLayout(
    private val up: MindMapLayoutManager
    , private val down: MindMapLayoutManager
) {
    /** Places the root's children and the topics below them along the axis through [root]. */
    fun arrange(
        root: NodeData<*>
        , tree: Tree<*>
    ) {
        // The root's floating topics are placed apart, and the children of a folded topic below it are hidden.
        val ids =
            if (root.parentId == null) root.children.filterNot { tree.isFloating(it) } else tree.visibleChildren(root)
        val axisY = root.path.centerY.dpVal
        val start = root.path.centerX.dpVal + halfWidth(root).dpVal + AXIS_GAP.dpVal
        // Where the next topic on the axis, and the next branch above and below it, may start.
        var axisCursor = start
        val sideCursors = mutableMapOf(up to start, down to start)
        val spans = mutableMapOf<String, Pair<Float, Float>>()
        ids.filterNot { tree.isSummary(it) }.forEachIndexed { index, id ->
            val manager = if (index % 2 == 0) up else down
            val node = tree.getNode(id)
            val halfNode = halfWidth(node).dpVal
            val (before, after) = manager.crossReach(node, tree).let { (before, after) ->
                maxOf(before.dpVal, halfNode) to maxOf(after.dpVal, halfNode)
            }
            val centerX = maxOf(axisCursor + halfNode, sideCursors.getValue(manager) + before)
            val placed = node.withCenter(Dp(centerX), Dp(axisY))
            tree.setNode(id, placed)
            manager.arrangeBelow(placed, tree)
            spans[id] = centerX - before to centerX + after
            axisCursor = centerX + halfNode + AXIS_GAP.dpVal
            sideCursors[manager] = centerX + after + AXIS_GAP.dpVal
        }
        ids.filter { tree.isSummary(it) }.forEach { summaryId ->
            val rangeIds = tree.summaryChildIds(summaryId).filter { it in spans }
            if (rangeIds.isEmpty()) return@forEach
            val left = rangeIds.minOf { spans.getValue(it).first }
            val right = rangeIds.maxOf { spans.getValue(it).second }
            var bottom = -Float.MAX_VALUE
            rangeIds.forEach { id ->
                tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
                    bottom = maxOf(bottom, node.path.centerY.dpVal + halfHeight(node).dpVal)
                }
            }
            val summary = tree.getNode(summaryId)
            val centerY = bottom + SUMMARY_OFFSET.value + halfHeight(summary).dpVal
            val placed = summary.withCenter(Dp((left + right) / 2), Dp(centerY))
            tree.setNode(summaryId, placed)
            down.arrangeBelow(placed, tree)
        }
    }
}
