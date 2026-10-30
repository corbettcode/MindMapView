package com.mindsync.library.command

import com.mindsync.library.MindMapManager
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.NodeGenerator


class AddNodeCommand(
    private val mindMapManager: MindMapManager,
    private val addNode: NodeData<*>,
    private val side: LayoutSide = LayoutSide.RIGHT,
) : MindMapCommand {
    override fun execute() {
        val parent = mindMapManager.getTree().getNode(addNode.parentId)
        val parentX = parent.path.centerX
        val parentY = parent.path.centerY
        val width = when (parent) {
            is CircleNodeData -> parent.path.radius
            is RectangleNodeData -> parent.path.width
            is SquareNodeData -> parent.path.width
            is LineNodeData -> parent.path.width
            is StadiumNodeData -> parent.path.width()
            is OvalNodeData -> parent.path.width()
        }
        val centerX = if (side.isLeft) parentX - width - Dp(20f) else parentX + width + Dp(20f)
        val updatedNode = when (addNode) {
            is CircleNodeData -> addNode.copy(path = addNode.path.copy(centerX = centerX, centerY = parentY))
            is RectangleNodeData -> addNode.copy(path = addNode.path.copy(centerX = centerX, centerY = parentY))
            is SquareNodeData -> addNode.copy(path = addNode.path.copy(centerX = centerX, centerY = parentY))
            is LineNodeData -> addNode.copy(path = addNode.path.copy(centerX = centerX, centerY = parentY))
            is StadiumNodeData -> addNode.copy(path = addNode.path.copy(centerX = centerX, centerY = parentY))
            is OvalNodeData -> addNode.copy(path = addNode.path.copy(centerX = centerX, centerY = parentY))
        }
        mindMapManager.addNode(updatedNode, addNode.description, side)
    }
}