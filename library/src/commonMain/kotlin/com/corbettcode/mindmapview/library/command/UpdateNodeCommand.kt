package com.corbettcode.mindmapview.library.command

import com.corbettcode.mindmapview.library.MindMapManager
import com.corbettcode.mindmapview.library.data.CircleNodeData
import com.corbettcode.mindmapview.library.data.NodeData
import com.corbettcode.mindmapview.library.data.OvalNodeData
import com.corbettcode.mindmapview.library.data.RectangleNodeData
import com.corbettcode.mindmapview.library.data.LineNodeData
import com.corbettcode.mindmapview.library.data.SquareNodeData
import com.corbettcode.mindmapview.library.data.StadiumNodeData


class UpdateNodeCommand(
    private val mindMapManager: MindMapManager,
    private val node: NodeData<*>,
    private val description: String,
) : MindMapCommand {
    override fun execute() {
        val selectedNode = when (node) {
            is CircleNodeData -> node.copy(description = description)
            is RectangleNodeData -> node.copy(description = description)
            is SquareNodeData -> node.copy(description = description)
            is LineNodeData -> node.copy(description = description)
            is StadiumNodeData -> node.copy(description = description)
            is OvalNodeData -> node.copy(description = description)
        }
        mindMapManager.update(selectedNode)
    }
}