package com.corbettcode.mindmapview.library.command

import com.corbettcode.mindmapview.library.MindMapManager
import com.corbettcode.mindmapview.library.data.NodeData

class RemoveNodeCommand(
    private val mindMapManager: MindMapManager,
    private val node: NodeData<*>,
) : MindMapCommand {
    override fun execute() {
        if (node.parentId == null) return
        mindMapManager.removeNode(node)
        mindMapManager.setSelectedNode(null)
    }
}