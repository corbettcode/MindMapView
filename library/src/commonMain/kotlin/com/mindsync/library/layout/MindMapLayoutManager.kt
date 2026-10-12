package com.mindsync.library.layout

import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp

/**
 * Arranges the nodes of a mind map on one [side] of their parent. Children are stacked vertically and
 * centered on their parent; the side they are placed on is up to the subclass.
 *
 * The root has a set of children for each side, so a layout manager only arranges and measures the
 * children of the root that are on its own side.
 */
abstract class MindMapLayoutManager {
    protected val horizontalSpacing = Dp(50f)
    protected val verticalSpacing = Dp(50f)

    abstract val side: LayoutSide

    /**
     * Returns the center x of [child] when its parent [parent] is centered on [parentCenterX].
     */
    protected abstract fun childCenterX(
        parentCenterX: Dp
        , parent: NodeData<*>
        , child: NodeData<*>
    ): Dp

    /**
     * Returns the root moved to where a tree that is [totalHeight] high fits.
     */
    open fun adjustRoot(
        root: NodeData<*>
        , tree: Tree<*>
        , totalHeight: Dp
    ): NodeData<*> {
        return adjustVertically(root, totalHeight)
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
        val childHeightSum = measureChildHeight(currentNodeData, tree)
        var startY = currentNodeData.path.centerY - (childHeightSum / 2)

        childrenOnSide(currentNodeData, tree).forEach { childId ->
            val child = tree.getNode(childId)
            val childHeight = measureChildHeight(child, tree)
            val newX = childCenterX(currentNodeData.path.centerX, currentNodeData, child)
            val newY = startY + (childHeight / 2)
            val newChild = moveNode(child, newX, newY)

            tree.setNode(childId, newChild)
            arrangeChildren(newChild, tree)
            startY += childHeight + verticalSpacing
        }
    }

    private fun adjustVertically(
        node: NodeData<*>
        , totalHeight: Dp
    ): NodeData<*> {
        return if (node.path.centerX.dpVal <= (totalHeight / 2).dpVal) {
            node.adjustPosition(horizontalSpacing, totalHeight)
        }
        else {
            node
        }
    }

    // Only the root has children on both sides.
    protected fun childrenOnSide(
        node: NodeData<*>
        , tree: Tree<*>
    ): List<String> {
        return if (node.parentId == null) node.children.filter { tree.sideOf(it) == side } else node.children
    }

    /**
     * The distance from the center of a parent to the edge its children are spaced from.
     */
    protected fun parentOffset(node: NodeData<*>): Dp {
        return when (node) {
            is RectangleNodeData -> node.path.width / 2
            is StadiumNodeData -> node.path.width() / 2
            is CircleNodeData -> node.path.radius / 2
        }
    }

    protected fun halfWidth(node: NodeData<*>): Dp {
        return when (node) {
            is RectangleNodeData -> node.path.width / 2
            is StadiumNodeData -> node.path.width() / 2
            is CircleNodeData -> node.path.radius
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
            is StadiumNodeData -> node.copy(path = node.path.copy(centerX = centerX, centerY = centerY))
        }
    }

    fun measureChildHeight(
        nodeData: NodeData<*>,
        tree: Tree<*>,
    ): Dp {
        var heightSum = Dp(0f)
        val children = childrenOnSide(nodeData, tree)

        if (children.isNotEmpty()) {
            children.forEach { childId ->
                val childNode = tree.getNode(childId)
                heightSum += measureChildHeight(childNode, tree)
            }
            heightSum += verticalSpacing * (children.size - 1)
        }
        else {
            heightSum =
                when (nodeData) {
                    is CircleNodeData -> nodeData.path.radius * 2
                    is RectangleNodeData -> nodeData.path.height
                    is StadiumNodeData -> nodeData.path.height()
                }
        }
        return heightSum
    }

    private var rightMost = Float.MIN_VALUE

    fun measureChildWidth(nodeData: NodeData<*>, tree: Tree<*>): Dp {
        calculateWidth(nodeData, tree)
        return Dp(rightMost)
    }

    private fun calculateWidth(nodeData: NodeData<*>, tree: Tree<*>) {
        rightMost = maxOf(rightMost, (nodeData.path.centerX + halfWidth(nodeData)).dpVal)
        nodeData.children.forEach { calculateWidth(tree.getNode(it), tree) }
    }
}
