package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp

/**
 * Layout manager that places the children of a node to its left.
 */
class MindMapLeftLayoutManager : MindMapLayoutManager() {
    override val side = LayoutSide.LEFT

    override fun childCenterX(
        parentCenterX: Dp
        , parent: NodeData<*>
        , child: NodeData<*>
    ): Dp {
        return parentCenterX - parentOffset(parent) - horizontalSpacing - halfWidth(child)
    }

    // The mind map cannot be panned to the left of x = 0, so the root is moved right until the tree fits.
    override fun adjustRoot(
        root: NodeData<*>
        , tree: Tree<*>
    ): NodeData<*> {
        val overflow = horizontalSpacing - leftMost(root, root.path.centerX, tree)
        return if (overflow.dpVal > 0f) moveNode(root, root.path.centerX + overflow, root.path.centerY) else root
    }

    private fun leftMost(
        node: NodeData<*>
        , centerX: Dp
        , tree: Tree<*>
    ): Dp {
        var leftMost = centerX - halfWidth(node)
        node.children.forEach { childId ->
            val child = tree.getNode(childId)
            val childLeftMost = leftMost(child, childCenterX(centerX, node, child), tree)
            if (childLeftMost.dpVal < leftMost.dpVal) leftMost = childLeftMost
        }
        return leftMost
    }
}
