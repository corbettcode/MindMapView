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

        childrenOnSide(currentNodeData, tree).forEach { childId ->
            val child = tree.getNode(childId)
            val childHeight = measureChildHeight(child, tree)
            val newX = childCenterX(currentNodeData.path.centerX, currentNodeData, child, tree)
            val newY = startY + (childHeight / 2)
            val newChild = moveNode(child, newX, newY)

            tree.setNode(childId, newChild)
            arrangeChildren(newChild, tree)
            startY += childHeight + verticalSpacing
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
        childrenOnSide(node, tree).forEach { childId ->
            val child = tree.getNode(childId)
            val childLeftMost = leftMost(child, childCenterX(centerX, node, child, tree), tree)
            if (childLeftMost.dpVal < leftMost.dpVal) leftMost = childLeftMost
        }
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

    private fun measureHeight(
        ids: List<String>
        , tree: Tree<*>
    ): Dp {
        var heightSum = Dp(0f)
        if (ids.isNotEmpty()) {
            ids.forEach { childId ->
                heightSum += measureChildHeight(tree.getNode(childId), tree)
            }
            heightSum += verticalSpacing * (ids.size - 1)
        }
        return heightSum
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
