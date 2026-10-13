package com.mindsync.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.shape
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.layout.MindMapLayoutManager
import com.mindsync.library.layout.MindMapLeftLayoutManager
import com.mindsync.library.layout.MindMapRightLayoutManager
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp

class MindMapManager {
    private lateinit var tree: Tree<*>
    private var selectNode: NodeData<*>? by mutableStateOf(null)
    private val rightLayoutManager = MindMapRightLayoutManager()
    private val leftLayoutManager = MindMapLeftLayoutManager()
    private var measureTextSize: MeasureTextSize? = null
    private var isMoving = false

    /** Whether the attributes of the nodes are shown, which makes the nodes that have any larger. */
    var showAttributes = true
        set(value) {
            field = value
            if (::tree.isInitialized) measureTextSize?.traverseTextHead(tree, value)
        }

    fun update(tree: Tree<*>) {
        this.tree = tree
        if (isMoving.not()) {
            measureTextSize?.traverseTextHead(this.tree, showAttributes)
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
        measureTextSize?.traverseTextHead(tree, showAttributes)
    }

    fun addNode(
        node: NodeData<*>
        , description: String
        , side: LayoutSide = LayoutSide.RIGHT
    ) {
        this.tree.addNode(node.id, node.parentId, description, node.shape, node.color, node.textColor, side, node.note)
        update(node)
    }

    // Unlike a node that is detached to be attached elsewhere, a removed node takes its arrow links along.
    fun removeNode(node: NodeData<*>) {
        this.tree.removeArrowLinksOfBranch(node.id)
        this.tree.doPreorderTraversal(node) { this.tree.setFolded(it.id, false) }
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

    fun getLayoutManager(side: LayoutSide): MindMapLayoutManager {
        return when (side) {
            LayoutSide.RIGHT -> rightLayoutManager
            LayoutSide.LEFT -> leftLayoutManager
        }
    }

    // The root is placed once for both of its sets of children, then each side is arranged around it.
    fun arrangeTree() {
        val totalHeight = measureHeight(tree.getRootNode())
        var root: NodeData<*> = tree.getRootNode()
        root = rightLayoutManager.adjustRoot(root, tree, totalHeight)
        root = leftLayoutManager.adjustRoot(root, tree, totalHeight)
        tree.setRootNode(root)
        rightLayoutManager.arrangeChildren(root, tree)
        leftLayoutManager.arrangeChildren(root, tree)
    }

    fun measureHeight(node: NodeData<*>): Dp {
        val rightHeight = rightLayoutManager.measureChildHeight(node, tree)
        val leftHeight = leftLayoutManager.measureChildHeight(node, tree)
        return if (leftHeight.dpVal > rightHeight.dpVal) leftHeight else rightHeight
    }

    fun measureWidth(node: NodeData<*>): Dp {
        return rightLayoutManager.measureChildWidth(node, tree)
    }

    fun deepCopyTree(): Map<String, NodeData<*>> {
        return this.tree.nodes.entries.associate { node ->
            node.key to node.value
        }
    }

    fun setMeasureTextSize(measureTextSize: MeasureTextSize?) {
        this.measureTextSize = measureTextSize
        if (::tree.isInitialized) {
            measureTextSize?.traverseTextHead(tree, showAttributes)
        }
    }

    fun getMeasureTextSize(): MeasureTextSize? {
        return this.measureTextSize
    }

    fun setTree(tree: Tree<*>) {
        this.tree = tree
        measureTextSize?.traverseTextHead(tree, showAttributes)
    }

    fun getTree(): Tree<*> {
        return this.tree
    }

}
