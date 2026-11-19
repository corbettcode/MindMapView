package com.corbettcode.mindmapview.library.command

import com.corbettcode.mindmapview.library.MindMapManager
import com.corbettcode.mindmapview.library.data.CircleNodeData
import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.OvalNodeData
import com.corbettcode.mindmapview.library.data.RectangleNodeData
import com.corbettcode.mindmapview.library.data.LineNodeData
import com.corbettcode.mindmapview.library.data.SquareNodeData
import com.corbettcode.mindmapview.library.data.StadiumNodeData
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.util.Dp
import com.corbettcode.mindmapview.library.util.NodeGenerator


class AddNodeCommand(
    private val mindMapManager: MindMapManager,
    private val addNode: NodeData<*>,
    private val side: LayoutSide = LayoutSide.RIGHT,
    private val index: Int? = null,
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
        mindMapManager.addNode(updatedNode, addNode.description, side, index)
    }
}