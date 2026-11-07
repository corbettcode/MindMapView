package com.mindsync.library.layout

import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/**
 * Arranges the nodes of a mind map on one [side] of their parent. Children are stacked vertically and
 * centered on their parent, to its left or right as [side] has it.
 *
 * The root has a set of children for each side, so a layout manager only arranges and measures the
 * children of the root that are on its own side. Those on a slanted side are stacked around a point out along
 * the line of that side, and kept clear of those straight out to the same side. The nodes below a folded node
 * are hidden: they take no room and are left where they are.
 *
 * A summary is not stacked with its siblings: it is centered on the siblings it is about, past the furthest out of
 * them and the nodes below them, with room for its bracket, and its own children go out from it. A summary taller
 * than its siblings takes the room it needs above and below them. Of summaries about the same siblings, the one
 * about more of them is further out.
 */
open class MindMapLayoutManager(val side: LayoutSide) {
    protected val horizontalSpacing = Dp(50f)
    protected val verticalSpacing = Dp(50f)

    private val slope = abs(tan(side.angleDegrees * PI.toFloat() / 180f))

    /**
     * Returns the root moved to where the nodes on this side of it fit, which have to be below y = 0 and, on
     * a left side, right of x = 0. [totalHeight] is the height of a tree that is centered on the root.
     */
    open fun adjustRoot(
        root: NodeData<*>
        , tree: Tree<*>
        , totalHeight: Dp
    ): NodeData<*> {
        val above = reachAbove(root, tree)
        val lowered = adjustVertically(root, if (above.dpVal * 2 > totalHeight.dpVal) above * 2 else totalHeight)
        if (!side.isLeft) return lowered
        val overflow = horizontalSpacing - leftMost(lowered, tree)
        return if (overflow.dpVal > 0f) moveNode(lowered, lowered.path.centerX + overflow, lowered.path.centerY) else lowered
    }

    fun arrangeNode(
        tree: Tree<*>
        , rootNodeData: NodeData<*>? = null
    ) {
        val root = rootNodeData ?: tree.getRootNode()
        val totalHeight = measureChildHeight(root, tree)
        val newHead =
            if (rootNodeData == null) {
                adjustRoot(root, tree, totalHeight).also { tree.setRootNode(it) }
            }
            else {
                adjustVertically(root, totalHeight)
            }
        arrangeChildren(newHead, tree)
    }

    /**
     * Arranges the nodes below [currentNodeData] that are on this side of it.
     */
    fun arrangeChildren(
        currentNodeData: NodeData<*>
        , tree: Tree<*>
    ) {
        val childHeightSum = measureChildrenHeight(currentNodeData, tree)
        val centerY = currentNodeData.path.centerY + childrenCenterOffsetY(currentNodeData, tree)
        var startY = centerY - (childHeightSum / 2)

        val childIds = childrenOnSide(currentNodeData, tree)
        val stackedIds = childIds.filterNot { tree.isSummary(it) }
        val gaps = stackGaps(stackedIds, childIds, tree)
        // Where each of the stacked children and the nodes below it are, from top to bottom, for the summaries.
        val slots = mutableMapOf<String, Pair<Dp, Dp>>()
        stackedIds.forEachIndexed { index, childId ->
            val child = tree.getNode(childId)
            val childHeight = measureChildHeight(child, tree)
            val (gapAbove, gapBelow) = gaps[index]
            startY += gapAbove
            val newX = childCenterX(currentNodeData.path.centerX, currentNodeData, child, tree)
            val newY = startY + (childHeight / 2)
            val newChild = moveNode(child, newX, newY)

            tree.setNode(childId, newChild)
            arrangeChildren(newChild, tree)
            slots[childId] = startY to startY + childHeight
            startY += childHeight + gapBelow + verticalSpacing
        }
        arrangeSummaries(childIds - stackedIds.toSet(), slots, tree)
    }

    // Each summary goes past the siblings it is about and the summaries about some of them that are already placed,
    // so those about fewer go first, nearer to them.
    private fun arrangeSummaries(
        summaryIds: List<String>
        , slots: Map<String, Pair<Dp, Dp>>
        , tree: Tree<*>
    ) {
        val placed = mutableListOf<Pair<String, Set<String>>>()
        summaryIds
            .map { it to tree.summaryChildIds(it).filter { id -> id in slots }.toSet() }
            .filter { (_, rangeIds) -> rangeIds.isNotEmpty() }
            .sortedBy { (_, rangeIds) -> rangeIds.size }
            .forEach { (summaryId, rangeIds) ->
                val top = rangeIds.minOf { slots.getValue(it).first.dpVal }
                val bottom = rangeIds.maxOf { slots.getValue(it).second.dpVal }
                val passedIds = rangeIds + placed.filter { (_, ids) -> ids.any { it in rangeIds } }.map { it.first }
                val edge = passedIds
                    .map { outerEdge(tree.getNode(it), tree).dpVal }
                    .let { edges -> if (side.isLeft) edges.min() else edges.max() }
                val summary = tree.getNode(summaryId)
                val offset = Dp(SUMMARY_OFFSET.value) + halfWidth(summary)
                val centerX = if (side.isLeft) Dp(edge) - offset else Dp(edge) + offset
                val newSummary = moveNode(summary, centerX, Dp((top + bottom) / 2))
                tree.setNode(summaryId, newSummary)
                arrangeChildren(newSummary, tree)
                placed.add(summaryId to rangeIds)
            }
    }

    // How far out to this side the node and the shown nodes below it reach, as they are placed now.
    private fun outerEdge(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val ownEdge = if (side.isLeft) node.path.centerX - halfWidth(node) else node.path.centerX + halfWidth(node)
        return tree.visibleChildren(node)
            .map { outerEdge(tree.getNode(it), tree) }
            .fold(ownEdge) { edge, other ->
                if (side.isLeft == (other.dpVal < edge.dpVal)) other else edge
            }
    }

    /** Returns how far the root and the nodes on this side of it reach above the center of the root. */
    fun reachAbove(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp = reach(root, tree, isAbove = true)

    /** Returns how far the root and the nodes on this side of it reach below the center of the root. */
    fun reachBelow(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp = reach(root, tree, isAbove = false)

    private fun reach(
        root: NodeData<*>
        , tree: Tree<*>
        , isAbove: Boolean
    ): Dp {
        val ownHalfHeight = ownHeight(root) / 2
        if (childrenOnSide(root, tree).isEmpty()) return ownHalfHeight
        val offset = childrenCenterOffsetY(root, tree)
        val halfHeight = measureChildrenHeight(root, tree) / 2
        val reach = if (isAbove) halfHeight - offset else halfHeight + offset
        return if (reach.dpVal > ownHalfHeight.dpVal) reach else ownHalfHeight
    }

    /** Returns the x of the left edge of the leftmost of [node] and the nodes below it on this side. */
    fun leftMost(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp = leftMost(node, node.path.centerX, tree)

    private fun leftMost(
        node: NodeData<*>
        , centerX: Dp
        , tree: Tree<*>
    ): Dp {
        var leftMost = centerX - halfWidth(node)
        val childIds = childrenOnSide(node, tree)
        val childLeftMosts = mutableMapOf<String, Dp>()
        childIds.filterNot { tree.isSummary(it) }.forEach { childId ->
            val child = tree.getNode(childId)
            childLeftMosts[childId] = leftMost(child, childCenterX(centerX, node, child, tree), tree)
        }
        // On a left side, a summary is further left than the siblings it is about: where [arrangeSummaries] puts it.
        if (side.isLeft) {
            val placed = mutableListOf<Pair<String, Set<String>>>()
            childIds
                .filter { tree.isSummary(it) }
                .map { it to tree.summaryChildIds(it).filter { id -> id in childLeftMosts }.toSet() }
                .filter { (_, rangeIds) -> rangeIds.isNotEmpty() }
                .sortedBy { (_, rangeIds) -> rangeIds.size }
                .forEach { (summaryId, rangeIds) ->
                    val passedIds =
                        rangeIds + placed.filter { (_, ids) -> ids.any { it in rangeIds } }.map { it.first }
                    val edge = passedIds.minOf { childLeftMosts.getValue(it).dpVal }
                    val summary = tree.getNode(summaryId)
                    val summaryCenterX = Dp(edge) - Dp(SUMMARY_OFFSET.value) - halfWidth(summary)
                    childLeftMosts[summaryId] = leftMost(summary, summaryCenterX, tree)
                    placed.add(summaryId to rangeIds)
                }
        }
        childLeftMosts.values.forEach { if (it.dpVal < leftMost.dpVal) leftMost = it }
        return leftMost
    }

    // The children of the root on a slanted side are as far out as needed to be on its line and clear of those
    // straight out to that side; below the root, children are a spacing away from their parent.
    private fun childCenterX(
        parentCenterX: Dp
        , parent: NodeData<*>
        , child: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val gap = if (parent.parentId == null) rootGap(parent, tree) else halfWidth(parent) + horizontalSpacing
        val offset = gap + halfWidth(child)
        return if (side.isLeft) parentCenterX - offset else parentCenterX + offset
    }

    private fun rootGap(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val straightGap = halfWidth(root) + horizontalSpacing
        return if (side.isSlanted) nearEdgeOffsetY(root, tree) / slope else straightGap
    }

    // How far the edge of the children on a slanted side of the root that is nearest to it is above or below it.
    private fun nearEdgeOffsetY(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val onLine = (halfWidth(root) + horizontalSpacing) * slope
        val straightChildren = root.children.filter { tree.sideOf(it) == side.horizontal }
        val clearOfStraight =
            if (straightChildren.isEmpty()) Dp(0f) else measureHeight(straightChildren, tree) / 2 + verticalSpacing
        return if (clearOfStraight.dpVal > onLine.dpVal) clearOfStraight else onLine
    }

    // How far below their parent the middle of the children on this side is: only those of the root on a
    // slanted side are not centered on it.
    private fun childrenCenterOffsetY(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        if (node.parentId != null || !side.isSlanted) return Dp(0f)
        val offset = nearEdgeOffsetY(node, tree) + measureChildrenHeight(node, tree) / 2
        return if (side.isUpper) Dp(0f) - offset else offset
    }

    // The mind map cannot be panned above y = 0, so the node is moved down until the nodes below it fit.
    private fun adjustVertically(
        node: NodeData<*>
        , totalHeight: Dp
    ): NodeData<*> {
        val topMost = totalHeight / 2 + verticalSpacing
        return if (node.path.centerY.dpVal < topMost.dpVal) moveNode(node, node.path.centerX, topMost) else node
    }

    // Only the root has children on every side.
    protected fun childrenOnSide(
        node: NodeData<*>
        , tree: Tree<*>
    ): List<String> {
        return if (node.parentId == null) node.children.filter { tree.sideOf(it) == side } else tree.visibleChildren(node)
    }

    protected fun halfWidth(node: NodeData<*>): Dp {
        return when (node) {
            is RectangleNodeData -> node.path.width / 2
            is SquareNodeData -> node.path.width / 2
            is LineNodeData -> node.path.width / 2
            is StadiumNodeData -> node.path.width() / 2
            is OvalNodeData -> node.path.width() / 2
            is CircleNodeData -> node.path.radius
        }
    }

    private fun ownHeight(node: NodeData<*>): Dp {
        return when (node) {
            is CircleNodeData -> node.path.radius * 2
            is RectangleNodeData -> node.path.height
            is SquareNodeData -> node.path.height
            is LineNodeData -> node.path.height
            is StadiumNodeData -> node.path.height()
            is OvalNodeData -> node.path.height()
        }
    }

    protected fun moveNode(
        node: NodeData<*>
        , centerX: Dp
        , centerY: Dp
    ): NodeData<*> {
        return when (node) {
            is CircleNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
            is RectangleNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
            is SquareNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
            is LineNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
            is StadiumNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
            is OvalNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
        }
    }

    /**
     * Returns the height the node and the nodes below it on this side take: that of its children, or its
     * own when it is the taller, as a node with a table of attributes may be.
     */
    fun measureChildHeight(
        nodeData: NodeData<*>,
        tree: Tree<*>,
    ): Dp {
        val childrenHeight = measureChildrenHeight(nodeData, tree)
        val ownHeight = ownHeight(nodeData)
        return if (childrenHeight.dpVal > ownHeight.dpVal) childrenHeight else ownHeight
    }

    // The height of the children of the node on this side, one above the other, or none without any.
    private fun measureChildrenHeight(
        nodeData: NodeData<*>,
        tree: Tree<*>,
    ): Dp = measureHeight(childrenOnSide(nodeData, tree), tree)

    // Summaries are not stacked, but take room above and below the siblings they are about when they need it.
    private fun measureHeight(
        ids: List<String>
        , tree: Tree<*>
    ): Dp {
        val stackedIds = ids.filterNot { tree.isSummary(it) }
        var heightSum = Dp(0f)
        if (stackedIds.isNotEmpty()) {
            stackedIds.forEach { childId ->
                heightSum += measureChildHeight(tree.getNode(childId), tree)
            }
            heightSum += verticalSpacing * (stackedIds.size - 1)
            stackGaps(stackedIds, ids, tree).forEach { (above, below) -> heightSum += above + below }
        }
        return heightSum
    }

    // The room each of the stacked children takes above and below it, for boundaries and for summaries of [allIds].
    private fun stackGaps(
        stackedIds: List<String>
        , allIds: List<String>
        , tree: Tree<*>
    ): List<Pair<Dp, Dp>> {
        val boundaryGaps = boundaryGaps(stackedIds, tree)
        val summaryIds = allIds.filter { tree.isSummary(it) }
        if (summaryIds.isEmpty()) return boundaryGaps
        val above = boundaryGaps.map { it.first.dpVal }.toMutableList()
        val below = boundaryGaps.map { it.second.dpVal }.toMutableList()
        val indexes = stackedIds.withIndex().associate { (index, id) -> id to index }
        summaryIds.forEach { summaryId ->
            val covered = tree.summaryChildIds(summaryId).mapNotNull { indexes[it] }
            if (covered.isEmpty()) return@forEach
            val first = covered.min()
            val last = covered.max()
            val rangeHeight = (first..last)
                .sumOf { measureChildHeight(tree.getNode(stackedIds[it]), tree).dpVal.toDouble() }
                .toFloat() + verticalSpacing.dpVal * (last - first)
            val excess = measureChildHeight(tree.getNode(summaryId), tree).dpVal - rangeHeight
            if (excess > 0f) {
                above[first] += excess / 2
                below[last] += excess / 2
            }
        }
        return stackedIds.indices.map { Dp(above[it]) to Dp(below[it]) }
    }

    // The room each of the children takes above and below it for the boundaries that start or end at it, as a
    // boundary is drawn that far outside the nodes it is around. With it, a boundary is as far from the nodes and
    // boundaries next to it as nodes are from each other, however many boundaries are within each other.
    private fun boundaryGaps(
        ids: List<String>
        , tree: Tree<*>
    ): List<Pair<Dp, Dp>> {
        val noGaps = ids.map { Dp(0f) to Dp(0f) }
        val parentId = ids.firstOrNull()?.let { tree.getNode(it).parentId } ?: return noGaps
        val boundaries = tree.boundaries.filter { it.parentId == parentId }
        if (boundaries.isEmpty()) return noGaps
        val above = IntArray(ids.size)
        val below = IntArray(ids.size)
        val indexes = ids.withIndex().associate { (index, id) -> id to index }
        boundaries.forEach { boundary ->
            val covered = tree.boundaryChildIds(boundary).mapNotNull { indexes[it] }
            if (covered.isEmpty()) return@forEach
            above[covered.min()]++
            below[covered.max()]++
        }
        val gap = Dp(BOUNDARY_PADDING.value)
        return ids.indices.map { gap * above[it] to gap * below[it] }
    }

    private var rightMost = Float.MIN_VALUE

    fun measureChildWidth(nodeData: NodeData<*>, tree: Tree<*>): Dp {
        rightMost = Float.MIN_VALUE
        calculateWidth(nodeData, tree)
        return Dp(rightMost)
    }

    private fun calculateWidth(nodeData: NodeData<*>, tree: Tree<*>) {
        rightMost = maxOf(rightMost, (nodeData.path.centerX + halfWidth(nodeData)).dpVal)
        tree.visibleChildren(nodeData).forEach { calculateWidth(tree.getNode(it), tree) }
    }
}
