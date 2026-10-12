package com.mindsync.library.animator

import com.mindsync.library.MindMapManager

/**
 * A class that implements the `AnimationStrategy` interface to provide tree change animations
 * in a mind map editor. It animates the transition of tree nodes from their initial positions
 * to their rearranged positions.
 *
 * @property mindMapManager An instance of `MindMapManager` used to manage and manipulate
 * the mind map's tree structure and nodes during the animation.
 * @property treeInvalidate A lambda function that invalidates and redraws the tree,
 * ensuring the UI reflects the animation progress.
 */
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