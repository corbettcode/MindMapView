package com.mindsync.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.shape
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.layout.MindMapLayoutManager
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp

class MindMapManager {
    private lateinit var tree: Tree<*>
    private var selectNode: NodeData<*>? by mutableStateOf(null)
    private val layoutManagers = LayoutSide.entries.associateWith { MindMapLayoutManager(it) }
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

    fun getLayoutManager(side: LayoutSide): MindMapLayoutManager = layoutManagers.getValue(side)

    // The root is placed once for all of its sets of children, then each side is arranged around it.
    // A root the user dragged stays where it was put, even where the tree reaches above or left of the mind map.
    fun arrangeTree() {
        val root = if (tree.isRootDragged) tree.getRootNode() else fittedRoot()
        tree.setRootNode(root)
        layoutManagers.values.forEach { it.arrangeChildren(root, tree) }
    }

    private fun fittedRoot(): NodeData<*> {
        val totalHeight = measureHeight(tree.getRootNode())
        return layoutManagers.values.fold(tree.getRootNode() as NodeData<*>) { root, manager ->
            manager.adjustRoot(root, tree, totalHeight)
        }
    }

    /**
     * Returns how far the tree reaches to the left of and above where it fits, as its x and y.
     */
    fun measureOverflow(): Pair<Dp, Dp> {
        val root = tree.getRootNode()
        val fittedRoot = fittedRoot()
        return fittedRoot.path.centerX - root.path.centerX to fittedRoot.path.centerY - root.path.centerY
    }

    /**
     * Returns the height of the node and the nodes below it, as if they were centered on it: twice as far as
     * they reach above or below it.
     */
    fun measureHeight(node: NodeData<*>): Dp {
        val reach = layoutManagers.values.maxOf { manager ->
            if (node.parentId == null) {
                maxOf(manager.reachAbove(node, tree).dpVal, manager.reachBelow(node, tree).dpVal)
            }
            else {
                manager.measureChildHeight(node, tree).dpVal / 2
            }
        }
        return Dp(reach * 2)
    }

    fun measureWidth(node: NodeData<*>): Dp {
        return getLayoutManager(LayoutSide.RIGHT).measureChildWidth(node, tree)
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
