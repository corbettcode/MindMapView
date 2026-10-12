package com.mindsync.library.layout

import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp

/**
 * Arranges the nodes of a mind map. Children are stacked vertically and centered on their parent; the
 * side of the parent they are placed on is up to the subclass.
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
     * Returns the root moved to where the whole tree fits. The root is kept where it is by default.
     */
    protected open fun adjustRoot(
        root: NodeData<*>
        , tree: Tree<*>
    ): NodeData<*> {
        return root
    }

    fun arrangeNode(
        tree: Tree<*>
        , rootNodeData: NodeData<*>? = null
    ) {
        val root = rootNodeData ?: tree.getRootNode()
        val totalHeight = measureChildHeight(root, tree)
        var newHead =
            if (root.path.centerX.dpVal <= (totalHeight / 2).dpVal) {
                root.adjustPosition(horizontalSpacing, totalHeight)
            }
            else {
                root
            } as NodeData<*>
        if (rootNodeData == null) {
            newHead = adjustRoot(newHead, tree)
            tree.setRootNode(newHead)
        }
        recurArrangeNode(newHead, tree)
    }

    private fun recurArrangeNode(
        currentNodeData: NodeData<*>
        , tree: Tree<*>
    ) {
        val childHeightSum = measureChildHeight(currentNodeData, tree)
        var startY = currentNodeData.path.centerY - (childHeightSum / 2)

        currentNodeData.children.forEach { childId ->
            val child = tree.getNode(childId)
            val childHeight = measureChildHeight(child, tree)
            val newX = childCenterX(currentNodeData.path.centerX, currentNodeData, child)
            val newY = startY + (childHeight / 2)
            val newChild = moveNode(child, newX, newY)

            tree.setNode(childId, newChild)
            recurArrangeNode(newChild, tree)
            startY += childHeight + verticalSpacing
        }
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

        if (nodeData.children.isNotEmpty()) {
            nodeData.children.forEach { childId ->
                val childNode = tree.getNode(childId)
                heightSum += measureChildHeight(childNode, tree)
            }
            heightSum += verticalSpacing * (nodeData.children.size - 1)
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
