package com.mindsync.library.layout

import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/**
 * Arranges the nodes of a mind map on one [side] of their parent, growing out of it in [direction]. Children are
 * stacked across that direction and centered on their parent: for a branch growing left or right, one above the
 * other, and for one growing down or up, side by side. The layout works along the main axis, the way the branch
 * grows, and the cross axis, the way the children are stacked, and is the same for every direction but for which
 * axis is x.
 *
 * The root has a set of children for each side in a mind map, so a layout manager only arranges and measures the
 * children of the root that are on its own side; with [isAllRootChildren], as in the other structures, it arranges
 * all of them but the floating topics. Those on a slanted side are stacked around a point out along the line of that
 * side, and kept clear of those straight out to the same side. The nodes below a folded node are hidden: they take
 * no room and are left where they are.
 *
 * A summary is not stacked with its siblings: it is centered on the siblings it is about, past the furthest out of
 * them and the nodes below them, with room for its bracket, and its own children go out from it. A summary larger
 * across than its siblings takes the room it needs on either side of them. Of summaries about the same siblings, the
 * one about more of them is further out.
 */
open class MindMapLayoutManager(
    val side: LayoutSide
    , val direction: GrowDirection = if (side.isLeft) GrowDirection.LEFT else GrowDirection.RIGHT
    , private val isAllRootChildren: Boolean = false
) {
    protected val horizontalSpacing = Dp(50f)
    protected val verticalSpacing = Dp(50f)

    private val isHorizontal = direction.isHorizontal

    // The space between a node and its children, and between siblings. Side by side, siblings are as wide as their
    // text, so they need less room between them than one above the other, and the map stays a width to read.
    private val mainSpacing = if (isHorizontal) horizontalSpacing else Dp(40f)
    private val crossSpacing = if (isHorizontal) verticalSpacing else Dp(20f)

    // Whether the branch grows towards the left or top of the map, where it may not reach past the edge.
    private val isBackward = direction.sign < 0f

    private val slope = abs(tan(side.angleDegrees * PI.toFloat() / 180f))

    /**
     * What lays out the branches of the topics with a structure of their own. Without it, every branch is laid out
     * here.
     */
    internal var ownedBranches: OwnedBranches? = null

    /**
     * Returns the root moved to where the nodes on this side of it fit, which have to be past the top and left of
     * the map: across the way they grow by half of [totalCross], the size across of a tree that is centered on the
     * root, and, for a branch growing up or left, along it too.
     */
    open fun adjustRoot(
        root: NodeData<*>
        , tree: Tree<*>
        , totalCross: Dp
    ): NodeData<*> {
        val above = reachAbove(root, tree)
        val lowered = adjustCross(root, if (above.dpVal * 2 > totalCross.dpVal) above * 2 else totalCross)
        if (!isBackward) return lowered
        val overflow = mainSpacing - backMost(lowered, tree)
        return if (overflow.dpVal > 0f) place(lowered, main(lowered) + overflow, cross(lowered)) else lowered
    }

    fun arrangeNode(
        tree: Tree<*>
        , rootNodeData: NodeData<*>? = null
    ) {
        val root = rootNodeData ?: tree.getRootNode()
        val totalCross = measureChildHeight(root, tree)
        val newHead =
            if (rootNodeData == null) {
                adjustRoot(root, tree, totalCross).also { tree.setRootNode(it) }
            }
            else {
                adjustCross(root, totalCross)
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
        val childrenCross = measureChildrenHeight(currentNodeData, tree)
        val center = cross(currentNodeData) + childrenCenterOffset(currentNodeData, tree)
        var start = center - (childrenCross / 2)

        val childIds = childrenOnSide(currentNodeData, tree)
        val stackedIds = childIds.filterNot { tree.isSummary(it) }
        val gaps = stackGaps(stackedIds, childIds, tree)
        // Where each of the stacked children and the nodes below it are across, in order, for the summaries.
        val slots = mutableMapOf<String, Pair<Dp, Dp>>()
        stackedIds.forEachIndexed { index, childId ->
            val child = tree.getNode(childId)
            val childCross = measureChildHeight(child, tree)
            val (gapBefore, gapAfter) = gaps[index]
            start += gapBefore
            val newMain = childMain(main(currentNodeData), currentNodeData, child, tree)
            val newChild = place(child, newMain, start + (ownedBox(child, tree)?.let(::before) ?: (childCross / 2)))

            tree.setNode(childId, newChild)
            arrangeBelow(newChild, tree)
            slots[childId] = start to start + childCross
            start += childCross + gapAfter + crossSpacing
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
                val first = rangeIds.minOf { slots.getValue(it).first.dpVal }
                val last = rangeIds.maxOf { slots.getValue(it).second.dpVal }
                val passedIds = rangeIds + placed.filter { (_, ids) -> ids.any { it in rangeIds } }.map { it.first }
                val edge = passedIds
                    .map { outerEdge(tree.getNode(it), tree).dpVal }
                    .let { edges -> if (isBackward) edges.min() else edges.max() }
                val summary = tree.getNode(summaryId)
                val offset = Dp(SUMMARY_OFFSET.value) + mainBack(summary, tree)
                val summaryMain = if (isBackward) Dp(edge) - offset else Dp(edge) + offset
                // A summary's own branch need not reach as far before it as after it, so its box is centered.
                val summaryCross = ownedBox(summary, tree)
                    ?.let { box -> Dp((first + last) / 2) + (before(box) - after(box)) / 2 }
                    ?: Dp((first + last) / 2)
                val newSummary = place(summary, summaryMain, summaryCross)
                tree.setNode(summaryId, newSummary)
                arrangeBelow(newSummary, tree)
                placed.add(summaryId to rangeIds)
            }
    }

    // How far out along the way this side grows the node and the shown nodes below it reach, as they are placed now.
    private fun outerEdge(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        ownedBox(node, tree)?.let { box ->
            return if (isBackward) main(node) - forward(box) else main(node) + forward(box)
        }
        val ownEdge = if (isBackward) main(node) - mainHalf(node) else main(node) + mainHalf(node)
        return tree.visibleChildren(node)
            .map { outerEdge(tree.getNode(it), tree) }
            .fold(ownEdge) { edge, other ->
                if (isBackward == (other.dpVal < edge.dpVal)) other else edge
            }
    }

    /**
     * Returns how far the root and the nodes on this side of it reach before the center of the root across the way
     * they grow: above it for a branch growing left or right, left of it for one growing down or up.
     */
    fun reachAbove(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp = reach(root, tree, isBefore = true)

    /** Returns how far the root and the nodes on this side of it reach after the center of the root, across. */
    fun reachBelow(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp = reach(root, tree, isBefore = false)

    private fun reach(
        root: NodeData<*>
        , tree: Tree<*>
        , isBefore: Boolean
    ): Dp {
        val ownHalf = crossSize(root) / 2
        if (childrenOnSide(root, tree).isEmpty()) return ownHalf
        val offset = childrenCenterOffset(root, tree)
        val half = measureChildrenHeight(root, tree) / 2
        val reach = if (isBefore) half - offset else half + offset
        return if (reach.dpVal > ownHalf.dpVal) reach else ownHalf
    }

    /**
     * Returns the x of the left edge of the leftmost of [node] and the nodes below it on this side, for a branch
     * growing left or right, or the y of the top edge of the topmost, for one growing down or up.
     */
    fun leftMost(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp = backMost(node, tree)

    private fun backMost(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp = backMost(node, main(node), tree)

    private fun backMost(
        node: NodeData<*>
        , nodeMain: Dp
        , tree: Tree<*>
    ): Dp {
        ownedBox(node, tree)?.let { box -> return nodeMain - (if (isHorizontal) box.left else box.top) }
        var backMost = nodeMain - mainHalf(node)
        val childIds = childrenOnSide(node, tree)
        val childBackMosts = mutableMapOf<String, Dp>()
        childIds.filterNot { tree.isSummary(it) }.forEach { childId ->
            val child = tree.getNode(childId)
            childBackMosts[childId] = backMost(child, childMain(nodeMain, node, child, tree), tree)
        }
        // Growing back, a summary is further back than the siblings it is about: where [arrangeSummaries] puts it.
        if (isBackward) {
            val placed = mutableListOf<Pair<String, Set<String>>>()
            childIds
                .filter { tree.isSummary(it) }
                .map { it to tree.summaryChildIds(it).filter { id -> id in childBackMosts }.toSet() }
                .filter { (_, rangeIds) -> rangeIds.isNotEmpty() }
                .sortedBy { (_, rangeIds) -> rangeIds.size }
                .forEach { (summaryId, rangeIds) ->
                    val passedIds =
                        rangeIds + placed.filter { (_, ids) -> ids.any { it in rangeIds } }.map { it.first }
                    val edge = passedIds.minOf { childBackMosts.getValue(it).dpVal }
                    val summary = tree.getNode(summaryId)
                    val summaryMain = Dp(edge) - Dp(SUMMARY_OFFSET.value) - mainBack(summary, tree)
                    childBackMosts[summaryId] = backMost(summary, summaryMain, tree)
                    placed.add(summaryId to rangeIds)
                }
        }
        childBackMosts.values.forEach { if (it.dpVal < backMost.dpVal) backMost = it }
        return backMost
    }

    // The children of the root on a slanted side are as far out as needed to be on its line and clear of those
    // straight out to that side; below the root, children are a spacing away from their parent.
    private fun childMain(
        parentMain: Dp
        , parent: NodeData<*>
        , child: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val gap = if (parent.parentId == null) rootGap(parent, tree) else mainHalf(parent) + mainSpacing
        val offset = gap + mainBack(child, tree)
        return if (isBackward) parentMain - offset else parentMain + offset
    }

    private fun rootGap(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val straightGap = mainHalf(root) + mainSpacing
        return if (isSlanted) nearEdgeOffset(root, tree) / slope else straightGap
    }

    // Only the root of a mind map has slanted sides, which grow left or right.
    private val isSlanted = side.isSlanted && !isAllRootChildren && isHorizontal

    // How far the edge of the children on a slanted side of the root that is nearest to it is above or below it.
    private fun nearEdgeOffset(
        root: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        val onLine = (mainHalf(root) + mainSpacing) * slope
        val straightChildren = root.children.filter { tree.sideOf(it) == side.horizontal && !tree.isFloating(it) }
        val clearOfStraight =
            if (straightChildren.isEmpty()) Dp(0f) else measureHeight(straightChildren, tree) / 2 + crossSpacing
        return if (clearOfStraight.dpVal > onLine.dpVal) clearOfStraight else onLine
    }

    // How far across from their parent the middle of the children on this side is: only those of the root on a
    // slanted side are not centered on it.
    private fun childrenCenterOffset(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp {
        if (node.parentId != null || !isSlanted) return Dp(0f)
        val offset = nearEdgeOffset(node, tree) + measureChildrenHeight(node, tree) / 2
        return if (side.isUpper) Dp(0f) - offset else offset
    }

    /**
     * Places the floating topic [node] at [offset] from the center of [root] and lays out the nodes below it on this
     * side of it.
     */
    fun arrangeFloating(
        root: NodeData<*>
        , node: NodeData<*>
        , offset: DpOffset
        , tree: Tree<*>
    ) {
        val placed = moveNode(node, root.path.centerX + offset.x, root.path.centerY + offset.y)
        tree.setNode(node.id, placed)
        arrangeBelow(placed, tree)
    }

    /** Returns [node] moved by [offset]. */
    fun movedBy(
        node: NodeData<*>
        , offset: DpOffset
    ): NodeData<*> = moveNode(node, node.path.centerX + offset.x, node.path.centerY + offset.y)

    /**
     * Returns how far right and down [root] has to go for the floating topic [node], at [offset] from it, and the
     * nodes below it on this side of it to be clear of the top and left of the mind map, or zero when they are.
     */
    fun floatingOverflow(
        root: NodeData<*>
        , node: NodeData<*>
        , offset: DpOffset
        , tree: Tree<*>
    ): DpOffset {
        val placed = moveNode(node, root.path.centerX + offset.x, root.path.centerY + offset.y)
        val back = backMost(node, main(placed), tree)
        val before = cross(placed) - crossBefore(node, tree)
        val left = if (isHorizontal) back else before
        val top = if (isHorizontal) before else back
        return DpOffset(
            Dp(maxOf(0f, (horizontalSpacing - left).dpVal))
            , Dp(maxOf(0f, (verticalSpacing - top).dpVal))
        )
    }

    // The mind map cannot be panned past its top and left, so the node is moved across until the nodes below it fit.
    private fun adjustCross(
        node: NodeData<*>
        , totalCross: Dp
    ): NodeData<*> {
        val nearest = totalCross / 2 + crossSpacing
        return if (cross(node).dpVal < nearest.dpVal) place(node, main(node), nearest) else node
    }

    // Only the root has children on every side.
    protected fun childrenOnSide(
        node: NodeData<*>
        , tree: Tree<*>
    ): List<String> {
        // A floating topic is placed where it was put, apart from the sides.
        return if (node.parentId == null) {
            node.children.filter { (isAllRootChildren || tree.sideOf(it) == side) && !tree.isFloating(it) }
        }
        else {
            tree.visibleChildren(node)
        }
    }

    /**
     * Returns how far [node] and the nodes below it reach before its center across the way this side grows, and
     * after it: half of [measureChildHeight] each for a branch laid out here.
     */
    internal fun crossReach(
        node: NodeData<*>
        , tree: Tree<*>
    ): Pair<Dp, Dp> {
        val box = ownedBox(node, tree) ?: return measureChildHeight(node, tree).let { it / 2 to it / 2 }
        return before(box) to after(box)
    }

    /**
     * Returns how far [node] and the nodes below it reach back from its center towards its parent, the way this side
     * grows: half of the node, or more for one with a structure of its own.
     */
    internal fun backReach(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp = mainBack(node, tree)

    private fun crossBefore(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp = crossReach(node, tree).first

    /** Lays out the nodes below [node], here or, for a topic with a structure of its own, in that. */
    internal fun arrangeBelow(
        node: NodeData<*>
        , tree: Tree<*>
    ) {
        val branches = ownedBranches
        if (branches != null && tree.branchStructureOf(node.id) != null) branches.arrangeBelow(node, tree)
        else arrangeChildren(node, tree)
    }

    // The box of the branch of a topic with a structure of its own, or null for one laid out here.
    private fun ownedBox(
        node: NodeData<*>
        , tree: Tree<*>
    ): BranchBox? {
        val branches = ownedBranches ?: return null
        if (tree.branchStructureOf(node.id) == null) return null
        return branches.boxOf(node, tree)
    }

    // How far a box reaches back towards the parent along the way this side grows, out away from it, and across
    // before and after the node.
    private fun back(box: BranchBox): Dp = when (direction) {
        GrowDirection.RIGHT -> box.left
        GrowDirection.LEFT -> box.right
        GrowDirection.DOWN -> box.top
        GrowDirection.UP -> box.bottom
    }

    private fun forward(box: BranchBox): Dp = when (direction) {
        GrowDirection.RIGHT -> box.right
        GrowDirection.LEFT -> box.left
        GrowDirection.DOWN -> box.bottom
        GrowDirection.UP -> box.top
    }

    private fun before(box: BranchBox): Dp = if (isHorizontal) box.top else box.left

    private fun after(box: BranchBox): Dp = if (isHorizontal) box.bottom else box.right

    // How far the node's branch reaches back towards its parent: half of the node, or its box's for one with a
    // structure of its own, which may reach back past it.
    private fun mainBack(
        node: NodeData<*>
        , tree: Tree<*>
    ): Dp = ownedBox(node, tree)?.let(::back) ?: mainHalf(node)

    // Where the node's center is along the way the branch grows, and across it.
    private fun main(node: NodeData<*>): Dp = if (isHorizontal) node.path.centerX else node.path.centerY

    private fun cross(node: NodeData<*>): Dp = if (isHorizontal) node.path.centerY else node.path.centerX

    // Half of how long the node is along the way the branch grows, and how long it is across.
    private fun mainHalf(node: NodeData<*>): Dp = if (isHorizontal) halfWidth(node) else ownHeight(node) / 2

    private fun crossSize(node: NodeData<*>): Dp = if (isHorizontal) ownHeight(node) else halfWidth(node) * 2

    // The node moved to [main] along the way the branch grows and [cross] across it.
    private fun place(
        node: NodeData<*>
        , main: Dp
        , cross: Dp
    ): NodeData<*> = if (isHorizontal) moveNode(node, main, cross) else moveNode(node, cross, main)

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
     * Returns how long across the way it grows the node and the nodes below it on this side take: that of its
     * children, or its own when it is the longer, as a node with a table of attributes may be. It is a height for a
     * branch growing left or right, and a width for one growing down or up.
     */
    fun measureChildHeight(
        nodeData: NodeData<*>,
        tree: Tree<*>,
    ): Dp {
        ownedBox(nodeData, tree)?.let { box -> return before(box) + after(box) }
        val childrenCross = measureChildrenHeight(nodeData, tree)
        val ownCross = crossSize(nodeData)
        return if (childrenCross.dpVal > ownCross.dpVal) childrenCross else ownCross
    }

    // How long across the children of the node on this side are, one after the other, or nothing without any.
    private fun measureChildrenHeight(
        nodeData: NodeData<*>,
        tree: Tree<*>,
    ): Dp = measureHeight(childrenOnSide(nodeData, tree), tree)

    // Summaries are not stacked, but take room on either side of the siblings they are about when they need it.
    private fun measureHeight(
        ids: List<String>
        , tree: Tree<*>
    ): Dp {
        val stackedIds = ids.filterNot { tree.isSummary(it) }
        var sum = Dp(0f)
        if (stackedIds.isNotEmpty()) {
            stackedIds.forEach { childId ->
                sum += measureChildHeight(tree.getNode(childId), tree)
            }
            sum += crossSpacing * (stackedIds.size - 1)
            stackGaps(stackedIds, ids, tree).forEach { (before, after) -> sum += before + after }
        }
        return sum
    }

    // The room each of the stacked children takes before and after it, for boundaries and for summaries of [allIds].
    private fun stackGaps(
        stackedIds: List<String>
        , allIds: List<String>
        , tree: Tree<*>
    ): List<Pair<Dp, Dp>> {
        val boundaryGaps = boundaryGaps(stackedIds, tree)
        val summaryIds = allIds.filter { tree.isSummary(it) }
        if (summaryIds.isEmpty()) return boundaryGaps
        val before = boundaryGaps.map { it.first.dpVal }.toMutableList()
        val after = boundaryGaps.map { it.second.dpVal }.toMutableList()
        val indexes = stackedIds.withIndex().associate { (index, id) -> id to index }
        summaryIds.forEach { summaryId ->
            val covered = tree.summaryChildIds(summaryId).mapNotNull { indexes[it] }
            if (covered.isEmpty()) return@forEach
            val first = covered.min()
            val last = covered.max()
            val range = (first..last)
                .sumOf { measureChildHeight(tree.getNode(stackedIds[it]), tree).dpVal.toDouble() }
                .toFloat() + crossSpacing.dpVal * (last - first)
            val excess = measureChildHeight(tree.getNode(summaryId), tree).dpVal - range
            if (excess > 0f) {
                before[first] += excess / 2
                after[last] += excess / 2
            }
        }
        return stackedIds.indices.map { Dp(before[it]) to Dp(after[it]) }
    }

    // The room each of the children takes before and after it for the boundaries that start or end at it, as a
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
        val before = IntArray(ids.size)
        val after = IntArray(ids.size)
        val indexes = ids.withIndex().associate { (index, id) -> id to index }
        boundaries.forEach { boundary ->
            val covered = tree.boundaryChildIds(boundary).mapNotNull { indexes[it] }
            if (covered.isEmpty()) return@forEach
            before[covered.min()]++
            after[covered.max()]++
        }
        val gap = Dp(BOUNDARY_PADDING.value)
        return ids.indices.map { gap * before[it] to gap * after[it] }
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
