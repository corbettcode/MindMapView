package com.mindsync.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.shape
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.layout.MindMapLayoutManager
import com.mindsync.library.layout.MindMapRightLayoutManager
import com.mindsync.library.util.Dp

class MindMapManager {
    private lateinit var tree: Tree<*>
    private var selectNode: NodeData<*>? by mutableStateOf(null)
    private var layout: MindMapLayoutManager by mutableStateOf(MindMapRightLayoutManager())
    private var measureTextSize: MeasureTextSize? = null
    private var isMoving = false

    fun update(tree: Tree<*>) {
        this.tree = tree
        if (isMoving.not()) {
            measureTextSize?.traverseTextHead(this.tree)
        }
    }

    fun update(target: NodeData<*>) {
        tree.updateNode(
            target.id,
            target.description,
            target.children,
            target.path.centerX,
            target.path.centerY
        )
        measureTextSize?.traverseTextHead(tree)
    }

    fun addNode(node: NodeData<*>, description: String) {
        this.tree.addNode(node.id, node.parentId, description, node.shape, node.color, node.textColor)
        update(node)
    }

    fun removeNode(node: NodeData<*>) {
        this.tree.removeNode(node.id)
    }

    fun setSelectedNode(node: NodeData<*>?) {
        this.selectNode = node
    }

    fun setMoving() {
        this.isMoving = true
    }

    fun setNotMoving() {
        this.isMoving = false
    }

    fun getMovingState(): Boolean {
        return this.isMoving
    }

    fun getSelectedNode(): NodeData<*>? {
        return this.selectNode
    }

    fun setLayoutManager(layoutManager: MindMapLayoutManager) {
        this.layout = layoutManager
    }

    fun getLayoutManager(): MindMapLayoutManager {
        return this.layout
    }

    fun arrangeTree() {
        layout.arrangeNode(this.tree)
    }

    fun measureHeight(node: NodeData<*>): Dp {
        return layout.measureChildHeight(node, tree)
    }

    fun measureWidth(node: NodeData<*>): Dp {
        return layout.measureChildWidth(node, tree)
    }

    fun deepCopyTree(): Map<String, NodeData<*>> {
        return this.tree.nodes.entries.associate { node ->
            node.key to node.value
        }
    }

    fun setMeasureTextSize(measureTextSize: MeasureTextSize?) {
        this.measureTextSize = measureTextSize
        if (::tree.isInitialized) {
            measureTextSize?.traverseTextHead(tree)
        }
    }

    fun getMeasureTextSize(): MeasureTextSize? {
        return this.measureTextSize
    }

    fun setTree(tree: Tree<*>) {
        this.tree = tree
        measureTextSize?.traverseTextHead(tree)
    }

    fun getTree(): Tree<*> {
        return this.tree
    }

}
