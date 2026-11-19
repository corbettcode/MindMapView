package com.corbettcode.mindmapview.library.layout

import com.corbettcode.mindmapview.library.data.DpOffset
import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.data.withCenter
import com.corbettcode.mindmapview.library.halfHeight
import com.corbettcode.mindmapview.library.halfWidth
import com.corbettcode.mindmapview.library.util.Dp

/** How far a topic's children are indented from it in a tree chart. */
internal val TREE_CHART_INDENT = Dp(30f)

/** The space between the rows of a column of a tree chart. */
private val ROW_GAP = Dp(12f)

/** The space between the columns of a tree chart, and below the root. */
private val COLUMN_GAP = Dp(40f)

/**
 * Lays a map out as a tree chart: the root's children in a row below it, each with the topics below it in a column
 * under it, every topic's children stacked down below it and indented from it, to the right when [isRight] and to the
 * left otherwise, as XMind's tree charts are. A summary in a column is past the rows it is about, centered on them;
 * a summary of the root's children is below the columns it is about, centered on them.
 */
internal class TreeChartLayout(private val isRight: Boolean) {
    // +1 when the columns grow right, -1 left.
    private val sign = if (isRight) 1f else -1f

    /**
     * What lays out the branches of the topics with a structure of their own among the rows and columns. Without it,
     * every branch is laid out here.
     */
    var ownedBranches: OwnedBranches? = null

    /**
     * Places the children of [root] and the topics below them, in columns in a row below it. [root] is the map's
     * root, or a topic whose branch is laid out as a tree chart of its own.
     */
    fun arrange(
        root: NodeData<*>
        , tree: Tree<*>
    ) {
        // The root's floating topics are placed apart, and the children of a folded topic below it are hidden.
        val ids =
            if (root.parentId == null) root.children.filterNot { tree.isFloating(it) } else tree.visibleChildren(root)
        val columnIds = ids.filterNot { tree.isSummary(it) }
        val top = bottomOf(root) + COLUMN_GAP.dpVal
        var cursor = 0f
        // Each column is placed anywhere, then moved to follow the one before it.
        val spans = mutableMapOf<String, Pair<Float, Float>>()
        columnIds.forEach { id ->
            placeColumn(tree.getNode(id), near = 0f, top = top, tree = tree)
            val (left, right) = horizontalSpan(id, tree)
            shift(id, cursor - left, 0f, tree)
            spans[id] = cursor to cursor + right - left
            cursor += right - left + COLUMN_GAP.dpVal
        }
        // The row is centered under the root.
        val rowWidth = (cursor - COLUMN_GAP.dpVal).coerceAtLeast(0f)
        val rowShift = root.path.centerX.dpVal - rowWidth / 2
        columnIds.forEach { shift(it, rowShift, 0f, tree) }
        ids.filter { tree.isSummary(it) }.forEach { summaryId ->
            val rangeIds = tree.summaryChildIds(summaryId).filter { it in spans }
            if (rangeIds.isEmpty()) return@forEach
            val left = rangeIds.minOf { spans.getValue(it).first } + rowShift
            val right = rangeIds.maxOf { spans.getValue(it).second } + rowShift
            val bottom = rangeIds.maxOf { bottomOfBranch(it, tree) }
            val summary = tree.getNode(summaryId)
            val summaryTop = bottom + SUMMARY_OFFSET.value
            placeColumn(summary, near = 0f, top = summaryTop, tree = tree)
            shift(summaryId, (left + right) / 2 - tree.getNode(summaryId).path.centerX.dpVal, 0f, tree)
        }
    }

    /** Places the floating topic [node] at [offset] from the center of [root], with its column below it. */
    fun arrangeFloating(
        root: NodeData<*>
        , node: NodeData<*>
        , offset: DpOffset
        , tree: Tree<*>
    ) {
        val centerX = root.path.centerX.dpVal + offset.x.dpVal
        val centerY = root.path.centerY.dpVal + offset.y.dpVal
        arrangeColumnAt(node, centerX, centerY, tree)
    }

    /** Places [node] centered on ([centerX], [centerY]), with the topics below it in its column. */
    fun arrangeColumnAt(
        node: NodeData<*>
        , centerX: Float
        , centerY: Float
        , tree: Tree<*>
    ) {
        placeColumn(
            node
            , near = centerX - sign * halfWidth(node).dpVal
            , top = centerY - halfHeight(node).dpVal
            , tree = tree
        )
    }

    // Places [node] with its near edge (its left, for columns growing right) at [near] and its top at [top], and its
    // shown children in rows below it, indented. Returns how far down the column reaches.
    private fun placeColumn(
        node: NodeData<*>
        , near: Float
        , top: Float
        , tree: Tree<*>
    ): Float {
        placeOwned(node, near, top, tree)?.let { return it }
        val halfWidth = halfWidth(node).dpVal
        val placed = node.withCenter(Dp(near + sign * halfWidth), Dp(top + halfHeight(node).dpVal))
        tree.setNode(node.id, placed)
        var bottom = bottomOf(placed)
        var y = bottom + ROW_GAP.dpVal
        val childIds = tree.visibleChildren(placed)
        val rows = mutableMapOf<String, Pair<Float, Float>>()
        childIds.filterNot { tree.isSummary(it) }.forEach { childId ->
            val rowBottom = placeColumn(tree.getNode(childId), near + sign * TREE_CHART_INDENT.dpVal, y, tree)
            rows[childId] = y to rowBottom
            bottom = maxOf(bottom, rowBottom)
            y = rowBottom + ROW_GAP.dpVal
        }
        childIds.filter { tree.isSummary(it) }.forEach { summaryId ->
            val rangeIds = tree.summaryChildIds(summaryId).filter { it in rows }
            if (rangeIds.isEmpty()) return@forEach
            val rangeTop = rangeIds.minOf { rows.getValue(it).first }
            val rangeBottom = rangeIds.maxOf { rows.getValue(it).second }
            val edge = rangeIds.map { horizontalSpan(it, tree) }
                .let { spans -> if (isRight) spans.maxOf { it.second } else spans.minOf { it.first } }
            val summary = tree.getNode(summaryId)
            val summaryNear = edge + sign * SUMMARY_OFFSET.value
            val summaryTop = (rangeTop + rangeBottom) / 2 - halfHeight(summary).dpVal
            bottom = maxOf(bottom, placeColumn(summary, summaryNear, summaryTop, tree))
        }
        return bottom
    }

    // A topic with a structure of its own is placed so its whole branch, laid out in that, has its near edge at [near]
    // and its top at [top]. Returns how far down it reaches, or null for a topic laid out here.
    private fun placeOwned(
        node: NodeData<*>
        , near: Float
        , top: Float
        , tree: Tree<*>
    ): Float? {
        val branches = ownedBranches ?: return null
        if (tree.branchStructureOf(node.id) == null) return null
        val box = branches.boxOf(node, tree)
        val nearReach = (if (isRight) box.left else box.right).dpVal
        val placed = node.withCenter(Dp(near + sign * nearReach), Dp(top + box.top.dpVal))
        tree.setNode(node.id, placed)
        branches.arrangeBelow(placed, tree)
        return top + box.top.dpVal + box.bottom.dpVal
    }

    // The left and right of the node and the shown nodes below it, as they are placed now.
    private fun horizontalSpan(
        id: String
        , tree: Tree<*>
    ): Pair<Float, Float> {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            left = minOf(left, node.path.centerX.dpVal - halfWidth(node).dpVal)
            right = maxOf(right, node.path.centerX.dpVal + halfWidth(node).dpVal)
        }
        return left to right
    }

    private fun bottomOfBranch(
        id: String
        , tree: Tree<*>
    ): Float {
        var bottom = -Float.MAX_VALUE
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { bottom = maxOf(bottom, bottomOf(it)) }
        return bottom
    }

    private fun bottomOf(node: NodeData<*>): Float = node.path.centerY.dpVal + halfHeight(node).dpVal

    // Moves the node and the shown nodes below it by ([dx], [dy]).
    private fun shift(
        id: String
        , dx: Float
        , dy: Float
        , tree: Tree<*>
    ) {
        tree.doVisiblePreorderTraversal(tree.getNode(id)) { node ->
            tree.setNode(node.id, node.withCenter(node.path.centerX + dx, node.path.centerY + dy))
        }
    }
}
