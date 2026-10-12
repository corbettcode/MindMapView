package com.mindsync.library.layout

import com.mindsync.library.data.NodeData
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp

/**
 * Layout manager that places the children of a node to its right.
 */
class MindMapRightLayoutManager : MindMapLayoutManager() {
    override val side = LayoutSide.RIGHT

    override fun childCenterX(
        parentCenterX: Dp
        , parent: NodeData<*>
        , child: NodeData<*>
    ): Dp {
        return parentCenterX + parentOffset(parent) + horizontalSpacing + halfWidth(child)
    }
}
