package com.mindsync.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mindsync.library.data.DpOffset
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.shape
import com.mindsync.library.layout.BranchBox
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.layout.MindMapLayoutManager
import com.mindsync.library.layout.OwnedBranches
import com.mindsync.library.layout.TimelineLayout
import com.mindsync.library.layout.TreeChartLayout
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.GrowDirection
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.MapStructure
import com.mindsync.library.model.NoteDisplay
import com.mindsync.library.util.Dp

class MindMapManager {
    private lateinit var tree: Tree<*>
    private var selectNode: NodeData<*>? by mutableStateOf(null)
    private val layoutManagers = LayoutSide.entries.associateWith { MindMapLayoutManager(it) }
    private var measureTextSize: MeasureTextSize? = null
    private var isMoving = false

    /**
     * How the attributes of the nodes without a way of their own are shown. Shown inline, as a table below their
     * text, they make the nodes larger.
     */
    var attributeDisplay = AttributeDisplay.INLINE
        set(value) {
            field = value
            if (::tree.isInitialized) measureTextSize?.traverseTextHead(tree, value, noteDisplay)
        }

    /**
     * How the notes of the nodes without a way of their own are shown. Shown inline, below their text, they
     * make the nodes larger.
     */
    var noteDisplay = NoteDisplay.FLYOVER
        set(value) {
            field = value
            if (::tree.isInitialized) measureTextSize?.traverseTextHead(tree, attributeDisplay, value)
        }

    fun update(tree: Tree<*>) {
        this.tree = tree
        if (isMoving.not()) {
            measureTextSize?.traverseTextHead(this.tree, attributeDisplay, noteDisplay)
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
        measureTextSize?.traverseTextHead(tree, attributeDisplay, noteDisplay)
    }

    fun addNode(
        node: NodeData<*>
        , description: String
        , side: LayoutSide = LayoutSide.RIGHT
        , index: Int? = null
    ) {
        this.tree.addNode(
            node.id
            , node.parentId
            , description
            , node.shape
            , node.color
            , node.textColor
            , side
            , node.note
            , index
        )
        update(node)
    }

    // Typing changes one node at a time, so only that node is measured again, not all of the tree.
    fun updateText(
        id: String
        , description: String
    ) {
        val node = tree.nodes[id] ?: return
        tree.updateNode(id, description, node.children, node.path.centerX, node.path.centerY)
        measureTextSize?.measureNode(tree, id, attributeDisplay, noteDisplay)
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

    // The layout of each way a branch grows in the structures other than the mind map, where it takes all of the
    // root's children.
    private val directionManagers = GrowDirection.entries.associateWith { direction ->
        MindMapLayoutManager(LayoutSide.RIGHT, direction, isAllRootChildren = true)
    }

    /** Returns the layout of the branch the node [id] is in, in the structure its children are laid out in. */
    fun layoutManagerFor(id: String): MindMapLayoutManager =
        if (tree.structureOf(id) == MapStructure.MIND_MAP) getLayoutManager(tree.sideOf(id))
        else directionManagers.getValue(tree.directionOf(id))

    // The boxes of the branches with a structure of their own measured in the layout under way, as one is measured by
    // laying it out, or null between layouts, when the tree may have changed.
    private var passBoxes: MutableMap<String, BranchBox>? = null

    private inline fun <R> inPass(block: () -> R): R {
        if (passBoxes != null) return block()
        passBoxes = mutableMapOf()
        try {
            return block()
        }
        finally {
            passBoxes = null
        }
    }

    // A branch with a structure of its own is measured by laying it out around its topic, wherever that is, as how
    // far it reaches from it does not depend on where it is.
    private val ownedBranches = object : OwnedBranches {
        override fun boxOf(
            owner: NodeData<*>
            , tree: Tree<*>
        ): BranchBox {
            passBoxes?.get(owner.id)?.let { return it }
            arrangeBelow(owner, tree)
            var left = 0f
            var right = 0f
            var top = 0f
            var bottom = 0f
            val centerX = owner.path.centerX.dpVal
            val centerY = owner.path.centerY.dpVal
            tree.doVisiblePreorderTraversal(owner) { node ->
                val nodeX = node.path.centerX.dpVal
                val nodeY = node.path.centerY.dpVal
                left = maxOf(left, centerX - nodeX + halfWidth(node).dpVal)
                right = maxOf(right, nodeX - centerX + halfWidth(node).dpVal)
                top = maxOf(top, centerY - nodeY + halfHeight(node).dpVal)
                bottom = maxOf(bottom, nodeY - centerY + halfHeight(node).dpVal)
            }
            return BranchBox(Dp(left), Dp(right), Dp(top), Dp(bottom)).also { passBoxes?.put(owner.id, it) }
        }

        override fun arrangeBelow(
            owner: NodeData<*>
            , tree: Tree<*>
        ) {
            layoutManagerFor(owner.id).arrangeChildren(owner, tree)
        }
    }

    init {
        (layoutManagers.values + directionManagers.values).forEach { it.ownedBranches = ownedBranches }
    }

    // The layouts the root's children are arranged with: one for each side of a mind map, or the structure's one.
    private fun rootManagers(): Collection<MindMapLayoutManager> =
        if (tree.structure == MapStructure.MIND_MAP) layoutManagers.values
        else listOf(directionManagers.getValue(tree.directionOf(tree.getRootNode().id)))

    // The layout of a floating topic's branch, which grows as the root's children do.
    private fun floatingManager(id: String): MindMapLayoutManager =
        if (tree.structure == MapStructure.MIND_MAP) getLayoutManager(LayoutSide.RIGHT) else layoutManagerFor(id)

    private val treeChartLayouts = listOf(true, false).associateWith { TreeChartLayout(isRight = it) }

    private val timelineLayout = TimelineLayout(
        directionManagers.getValue(GrowDirection.UP)
        , directionManagers.getValue(GrowDirection.DOWN)
    )

    private fun treeChartLayout(): TreeChartLayout =
        treeChartLayouts.getValue(tree.structure == MapStructure.TREE_RIGHT)

    /** Lays out the nodes below [node], where it is, as the map's structure has them: while it is dragged, say. */
    fun arrangeBranch(node: NodeData<*>) = inPass {
        if (tree.structure.isTreeChart && tree.structureOwnerOf(node.id) == tree.getRootNode().id) {
            treeChartLayout().arrangeColumnAt(node, node.path.centerX.dpVal, node.path.centerY.dpVal, tree)
        }
        else {
            layoutManagerFor(node.id).arrangeNode(tree, node)
        }
    }

    // The root is placed once for all of its sets of children, then each side is arranged around it.
    // A root the user dragged stays where it was put, even where the tree reaches above or left of the mind map.
    fun arrangeTree(): Unit = inPass {
        if (tree.structure.isTreeChart || tree.structure == MapStructure.TIMELINE) return arrangeWhole()
        val root = if (tree.isRootDragged) tree.getRootNode() else fittedRoot()
        tree.setRootNode(root)
        rootManagers().forEach { it.arrangeChildren(root, tree) }
        // Floating topics are where they were put in relation to the root, with the nodes below them growing as the
        // root's children do.
        floatingTopics().forEach { (node, offset) ->
            floatingManager(node.id).arrangeFloating(root, node, offset, tree)
        }
    }

    private fun fittedRoot(): NodeData<*> {
        val managers = rootManagers()
        val totalCross = if (tree.structure == MapStructure.MIND_MAP) {
            measureHeight(tree.getRootNode())
        }
        else {
            managers.single().measureChildHeight(tree.getRootNode(), tree)
        }
        val root = managers.fold(tree.getRootNode() as NodeData<*>) { root, manager ->
            manager.adjustRoot(root, tree, totalCross)
        }
        // A floating topic above or left of the rest moves them all, for it to be on the mind map too.
        val overflows = floatingTopics().map { (node, offset) ->
            floatingManager(node.id).floatingOverflow(root, node, offset, tree)
        }
        val shiftX = overflows.maxOfOrNull { it.x.dpVal } ?: 0f
        val shiftY = overflows.maxOfOrNull { it.y.dpVal } ?: 0f
        if (shiftX == 0f && shiftY == 0f) return root
        return getLayoutManager(LayoutSide.RIGHT).movedBy(root, DpOffset(Dp(shiftX), Dp(shiftY)))
    }

    // A tree chart or a timeline is laid out around the root where it is, then moved as a whole to be clear of the
    // top and left of the map when it is not, unless the root was put where it is.
    private fun arrangeWhole() {
        layOutWhole(tree.getRootNode())
        if (tree.isRootDragged) return
        val (shiftX, shiftY) = wholeOverflow()
        if (shiftX.dpVal == 0f && shiftY.dpVal == 0f) return
        val root = getLayoutManager(LayoutSide.RIGHT).movedBy(tree.getRootNode(), DpOffset(shiftX, shiftY))
        tree.setRootNode(root)
        layOutWhole(root)
    }

    private fun layOutWhole(root: NodeData<*>) {
        if (tree.structure == MapStructure.TIMELINE) {
            timelineLayout.arrange(root, tree)
            floatingTopics().forEach { (node, offset) ->
                directionManagers.getValue(GrowDirection.DOWN).arrangeFloating(root, node, offset, tree)
            }
            return
        }
        val layout = treeChartLayout()
        layout.arrange(root, tree)
        floatingTopics().forEach { (node, offset) -> layout.arrangeFloating(root, node, offset, tree) }
    }

    // How far right and down the map, as it is laid out now, has to go to be clear of the top and left of the map.
    private fun wholeOverflow(): Pair<Dp, Dp> {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        tree.doVisiblePreorderTraversal { node ->
            left = minOf(left, (node.path.centerX - halfWidth(node)).dpVal)
            top = minOf(top, (node.path.centerY - halfHeight(node)).dpVal)
        }
        return Dp(maxOf(0f, TREE_CHART_MARGIN - left)) to Dp(maxOf(0f, TREE_CHART_MARGIN - top))
    }

    private fun floatingTopics(): List<Pair<NodeData<*>, DpOffset>> = tree.getRootNode().children.mapNotNull { id ->
        tree.floatingOffsetOf(id)?.let { tree.getNode(id) to it }
    }

    /**
     * Returns how far the tree reaches to the left of and above where it fits, as its x and y.
     */
    fun measureOverflow(): Pair<Dp, Dp> = inPass {
        if (tree.structure.isTreeChart || tree.structure == MapStructure.TIMELINE) return@inPass wholeOverflow()
        val root = tree.getRootNode()
        val fittedRoot = fittedRoot()
        fittedRoot.path.centerX - root.path.centerX to fittedRoot.path.centerY - root.path.centerY
    }

    /**
     * Returns the height of the node and the nodes below it, as if they were centered on it: twice as far as
     * they reach above or below it.
     */
    fun measureHeight(node: NodeData<*>): Dp = inPass {
        val reach = layoutManagers.values.maxOf { manager ->
            if (node.parentId == null) {
                maxOf(manager.reachAbove(node, tree).dpVal, manager.reachBelow(node, tree).dpVal)
            }
            else {
                manager.measureChildHeight(node, tree).dpVal / 2
            }
        }
        Dp(reach * 2)
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
            measureTextSize?.traverseTextHead(tree, attributeDisplay, noteDisplay)
        }
    }

    fun getMeasureTextSize(): MeasureTextSize? {
        return this.measureTextSize
    }

    fun setTree(tree: Tree<*>) {
        this.tree = tree
        measureTextSize?.traverseTextHead(tree, attributeDisplay, noteDisplay)
    }

    fun getTree(): Tree<*> {
        return this.tree
    }

}

// How far a tree chart is kept from the top and left of the map, as the other layouts are.
private const val TREE_CHART_MARGIN = 50f
