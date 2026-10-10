package com.mindsync.library.animator

import com.mindsync.library.MindMapManager

class TreeChangeAnimation(
    private val mindMapManager: MindMapManager,
    private val treeInvalidate: () -> Unit,
) : AnimationStrategy {
    override suspend fun animate() {
        val preTree = mindMapManager.deepCopyTree()
        mindMapManager.arrangeTree()
        val postTree = mindMapManager.deepCopyTree()

        AnimationUtils.animateProgress { progress ->
            preTree.entries.forEach { (nodeId, from) ->
                val to = postTree[nodeId] ?: return@forEach
                val fromNode = from
                val toNode = to
                val currentX =
                    fromNode.path.centerX + (toNode.path.centerX - fromNode.path.centerX) * progress
                val currentY =
                    fromNode.path.centerY + (toNode.path.centerY - fromNode.path.centerY) * progress

                mindMapManager.getTree()
                    .updateNode(nodeId, toNode.description, toNode.children, currentX, currentY)
            }
            treeInvalidate()
        }
    }
}