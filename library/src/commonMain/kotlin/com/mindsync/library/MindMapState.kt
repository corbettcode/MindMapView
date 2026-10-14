package com.mindsync.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import com.mindsync.library.animator.AnimationStrategy
import com.mindsync.library.animator.FitAnimation
import com.mindsync.library.animator.MindMapAnimator
import com.mindsync.library.animator.TreeChangeAnimation
import com.mindsync.library.command.AddNodeCommand
import com.mindsync.library.command.RemoveNodeCommand
import com.mindsync.library.command.UpdateNodeCommand
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OutlineNode
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.shape
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import com.mindsync.library.util.IdGenerator
import com.mindsync.library.util.NodeGenerator
import com.mindsync.library.util.Px
import com.mindsync.library.util.RichText
import com.mindsync.library.util.toDp
import com.mindsync.library.util.toPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun rememberMindMapState(tree: Tree<*> = remember { Tree<Any>() }): MindMapState {
    return remember(tree) { MindMapState(tree) }
}

@Stable
class MindMapState(
    tree: Tree<*> = Tree<Any>()
) {
    val mindMapManager = MindMapManager()
    private val mindMapAnimator = MindMapAnimator()
    private val pendingAnimations = mutableListOf<AnimationStrategy>()
    private var scope: CoroutineScope? = null
    private var treeChangeJob: Job? = null
    private var density = Density(1f)
    private var viewportSize = Size.Zero
    private var addNode: NodeData<*>? = null
    private var copiedBranch: CopiedBranch? by mutableStateOf(null)
    // The nodes that are selected besides selectedNode, in the order they were selected.
    private var otherSelectedIds by mutableStateOf(listOf<String>())
    // The node a range of nodes is selected from: the one selected alone, or added or taken out, last.
    private var rangeAnchorId: String? = null

    var scaleFactor by mutableFloatStateOf(DEFAULT_ZOOM)
        private set
    var currentPoint by mutableStateOf(Offset.Zero)
        private set
    internal var attachedNode: NodeData<*>? by mutableStateOf(null)
        private set
    internal var drawTick by mutableIntStateOf(0)
        private set

    // Where the pointer is, in the tree, while an arrow link is being dragged from the selected node.
    internal var linkDragPoint: Offset? by mutableStateOf(null)
        private set

    val tree: Tree<*> get() = mindMapManager.getTree()
    /** The node selected last, which the actions that take one node act on. */
    val selectedNode: NodeData<*>? get() = mindMapManager.getSelectedNode()

    /** The nodes that are selected, in the order they were selected: [selectedNode] is the last of them. */
    val selectedNodes: List<NodeData<*>>
        get() = otherSelectedIds.mapNotNull { tree.nodes[it] } + listOfNotNull(selectedNode)

    /**
     * The selected nodes that are not below another selected one, in the order of the tree. Each of them
     * stands for its branch, which the nodes selected below it are part of already.
     */
    val selectedBranches: List<NodeData<*>>
        get() {
            val ids = selectedNodes.map { it.id }.toSet()
            val branches = mutableListOf<NodeData<*>>()
            fun traverse(node: NodeData<*>) {
                if (node.id in ids) branches.add(node) else node.children.forEach { traverse(tree.getNode(it)) }
            }
            if (ids.isNotEmpty()) traverse(tree.getRootNode())
            return branches
        }

    private var hoveredNodeId by mutableStateOf<String?>(null)

    /** The node the mouse pointer is over while no button is pressed, or null when it is over none. */
    val hoveredNode: NodeData<*>? get() = hoveredNodeId?.let { tree.nodes[it] }

    /** Where in the bounds of the mind map the mouse pointer last was. */
    var hoverPosition by mutableStateOf(Offset.Zero)
        private set

    /** The arrow links of the tree. */
    val arrowLinks: List<ArrowLink>
        get() {
            // The tree is no state of its own, so reading the tick is what tells a reader of a change.
            drawTick
            return tree.arrowLinks
        }

    private var attributesShown by mutableStateOf(true)

    /**
     * Whether the attributes of the nodes are shown, as a table below the text of each node that has any.
     * Setting it rearranges the tree, as the nodes change size.
     */
    var showAttributes: Boolean
        get() = attributesShown
        set(value) {
            attributesShown = value
            mindMapManager.showAttributes = value
            animateTreeChange()
        }

    private var defaultSide by mutableStateOf(LayoutSide.RIGHT)

    /**
     * The side of the root new children of the root are added to unless [addNode] is given one. Setting
     * it also moves every child of the root to that side and rearranges the tree.
     */
    var layoutSide: LayoutSide
        get() = defaultSide
        set(value) {
            defaultSide = value
            tree.getRootNode().children.forEach { tree.setRootChildSide(it, value) }
            animateTreeChange()
        }

    init {
        mindMapManager.setTree(tree)
    }

    fun setTree(tree: Tree<*>) {
        if (mindMapManager.getSelectedNode()?.id !in tree.nodes) mindMapManager.setSelectedNode(null)
        otherSelectedIds = emptyList()
        mindMapManager.setTree(tree)
        placeRootIfNeeded()
        invalidate()
    }

    fun addNode(
        description: String,
        shape: NodeShape = NodeShape.RECTANGLE,
        color: Color = shape.defaultColor,
        textColor: Color = shape.defaultTextColor,
        side: LayoutSide = layoutSide,
        note: String = "",
    ) {
        mindMapManager.getSelectedNode()?.let { node ->
            val addNode: NodeData<*> = when (shape) {
                NodeShape.CIRCLE ->
                    NodeGenerator.makeNode(CircleNodeData::class, description, node.id)
                        .copy(color = color, textColor = textColor, note = note)
                NodeShape.RECTANGLE ->
                    NodeGenerator.makeNode(RectangleNodeData::class, description, node.id)
                        .copy(color = color, textColor = textColor, note = note)
                NodeShape.STADIUM ->
                    NodeGenerator.makeNode(StadiumNodeData::class, description, node.id)
                        .copy(color = color, textColor = textColor, note = note)
                NodeShape.OVAL ->
                    NodeGenerator.makeNode(OvalNodeData::class, description, node.id)
                        .copy(color = color, textColor = textColor, note = note)
            }
            this.addNode = addNode
            // Only the root has a set of children for each side; below it a child is on the side of its parent.
            val addSide = if (node.parentId == null) side else tree.sideOf(node.id)
            val addNodeCommand = AddNodeCommand(mindMapManager, addNode, addSide)
            addNodeCommand.execute()
        }
        animateTreeChange()
    }

    fun getAddNode(): NodeData<*>? {
        return this.addNode
    }

    /** Selects the node alone, or no node when it is null. */
    fun select(node: NodeData<*>?) {
        otherSelectedIds = emptyList()
        rangeAnchorId = node?.id
        mindMapManager.setSelectedNode(node)
        invalidate()
    }

    /**
     * Adds the node to the selected nodes as [selectedNode], or takes it out of them when it is one of them
     * already. The node selected before it is then [selectedNode] again.
     */
    fun toggleSelected(id: String) {
        val node = tree.nodes[id] ?: return
        rangeAnchorId = id
        val selected = mindMapManager.getSelectedNode()
        when {
            selected?.id == id -> {
                val previous = otherSelectedIds.lastOrNull()
                otherSelectedIds = otherSelectedIds.dropLast(1)
                mindMapManager.setSelectedNode(previous?.let { tree.nodes[it] })
            }
            id in otherSelectedIds -> otherSelectedIds = otherSelectedIds - id
            else -> {
                otherSelectedIds = otherSelectedIds + listOfNotNull(selected?.id)
                mindMapManager.setSelectedNode(node)
            }
        }
        invalidate()
    }

    /**
     * Selects the nodes from the one the range starts at, the node selected alone or added or taken out
     * last, to the node with the id [id], which is then [selectedNode]. Of two nodes of one parent, on one
     * side of the root, the range is their siblings between them; of other nodes, it is the nodes shown
     * between them, the root and the branches on its right coming before those on its left, each from top
     * to bottom. The start of the range stays where it is, for the next range to start there too.
     */
    fun selectRange(id: String) {
        val node = tree.nodes[id] ?: return
        val anchor = rangeAnchorId?.let { tree.nodes[it] } ?: mindMapManager.getSelectedNode()
        val order = visibleOrder()
        val from = anchor?.let { order.indexOf(it.id) } ?: -1
        val to = order.indexOf(id)
        if (anchor == null || from == -1 || to == -1) {
            select(node)
            return
        }
        val isSiblings = anchor.parentId == node.parentId && tree.sideOf(anchor.id) == tree.sideOf(id)
        val range = order
            .subList(minOf(from, to), maxOf(from, to) + 1)
            .filter { !isSiblings || tree.getNode(it).parentId == node.parentId }
        // The range is selected towards the node it ends at, which is selected last.
        otherSelectedIds = (if (from <= to) range else range.reversed()) - id
        mindMapManager.setSelectedNode(node)
        invalidate()
    }

    /**
     * Selects all of the nodes that are shown but the root, so they can be removed or cut together; those
     * hidden in a folded node are not selected. The last of them is [selectedNode], and the start of the
     * next range. Nothing changes when the root is the only node.
     */
    fun selectAll() {
        val ids = visibleOrder() - tree.getRootNode().id
        val last = ids.lastOrNull() ?: return
        otherSelectedIds = ids.dropLast(1)
        rangeAnchorId = last
        mindMapManager.setSelectedNode(tree.getNode(last))
        invalidate()
    }

    // The nodes that are shown, in the order a range of them is selected in.
    private fun visibleOrder(): List<String> {
        val root = tree.getRootNode()
        val ids = mutableListOf(root.id)
        listOf(LayoutSide.RIGHT, LayoutSide.LEFT).forEach { side ->
            tree.visibleChildren(root)
                .filter { tree.sideOf(it) == side }
                .forEach { branchId -> tree.doVisiblePreorderTraversal(tree.getNode(branchId)) { ids.add(it.id) } }
        }
        return ids
    }

    /** Removes the selected nodes and the nodes below them. The root is not removed. */
    fun removeNode() {
        val nodes = selectedBranches.filter { it.parentId != null }
        if (nodes.isEmpty()) return
        nodes.forEach { RemoveNodeCommand(mindMapManager, it).execute() }
        otherSelectedIds = emptyList()
        animateTreeChange()
    }

    /** Whether there is a node that was copied with [copyNode] to paste with [pasteNode]. */
    val hasCopiedNode: Boolean get() = copiedBranch != null

    /**
     * Copies the selected nodes and the nodes below them, with their attributes and the arrow links between
     * them, as they are now: a node that is changed or removed afterwards is pasted as it was when it was
     * copied. An arrow link to or from a node that was not copied is left out, as it has no end to paste.
     */
    fun copyNode() {
        val branches = selectedBranches
        if (branches.isEmpty()) return
        val ids = mutableSetOf<String>()
        branches.forEach { branch -> tree.doPreorderTraversal(branch) { ids.add(it.id) } }
        copiedBranch = CopiedBranch(
            branches.map { copyBranch(it) }
            , tree.arrowLinks.filter { it.sourceId in ids && it.targetId in ids }
        )
    }

    /**
     * Copies the selected nodes as [copyNode] does, then removes them. Nothing is cut when the root is one
     * of them, as it is neither copied without the rest of the tree nor removed.
     */
    fun cutNode() {
        val branches = selectedBranches
        if (branches.isEmpty() || branches.any { it.parentId == null }) return
        copyNode()
        removeNode()
    }

    private fun copyBranch(node: NodeData<*>): CopiedNode = CopiedNode(
        node = node
        , attributes = tree.attributesOf(node.id)
        , children = node.children.map { copyBranch(tree.getNode(it)) }
    )

    /**
     * Adds what was copied with [copyNode] as the last child of the selected node, on [side] of the root
     * when that is the selected node. It can be pasted again and again, each time as nodes of their own.
     */
    fun pasteNode(side: LayoutSide = layoutSide) {
        val parent = mindMapManager.getSelectedNode() ?: return
        val copied = copiedBranch ?: return
        // The id each copied node is pasted with, as the copied nodes may still be in the tree.
        val pastedIds = mutableMapOf<String, String>()
        fun paste(
            branch: CopiedNode
            , parentId: String
        ) {
            val id = IdGenerator.makeRandomNodeId()
            val node = branch.node
            tree.addNode(id, parentId, node.description, node.shape, node.color, node.textColor, side, node.note)
            tree.setAttributes(id, branch.attributes)
            pastedIds[node.id] = id
            branch.children.forEach { paste(it, id) }
        }
        copied.roots.forEach { paste(it, parent.id) }
        copied.arrowLinks.forEach { link ->
            tree.addArrowLink(
                link.copy(
                    id = IdGenerator.makeRandomNodeId()
                    , sourceId = pastedIds.getValue(link.sourceId)
                    , targetId = pastedIds.getValue(link.targetId)
                )
            )
        }
        growOutOf(parent.id, pastedIds.values)
    }

    /**
     * Adds the [nodes] and the nodes below them as the last children of the selected node, on [side] of the
     * root when that is the selected node. Each of them is a [shape] in [color] with text in [textColor].
     */
    fun addNodes(
        nodes: List<OutlineNode>
        , shape: NodeShape = NodeShape.RECTANGLE
        , color: Color = shape.defaultColor
        , textColor: Color = shape.defaultTextColor
        , side: LayoutSide = layoutSide
    ) {
        val parent = mindMapManager.getSelectedNode() ?: return
        if (nodes.isEmpty()) return
        val addedIds = mutableListOf<String>()
        fun add(
            node: OutlineNode
            , parentId: String
        ) {
            val id = IdGenerator.makeRandomNodeId()
            tree.addNode(id, parentId, node.description, shape, color, textColor, side)
            addedIds.add(id)
            node.children.forEach { add(it, id) }
        }
        nodes.forEach { add(it, parent.id) }
        growOutOf(parent.id, addedIds)
    }

    // The nodes come out of the node they were added to, as a node that is added on its own does.
    private fun growOutOf(
        parentId: String
        , ids: Collection<String>
    ) {
        val parentPath = tree.getNode(parentId).path
        ids.forEach { id ->
            val node = tree.getNode(id)
            tree.updateNode(id, node.description, node.children, parentPath.centerX, parentPath.centerY)
        }
        mindMapManager.update(tree)
        animateTreeChange()
    }

    /**
     * Moves the branches the selected nodes are in, the children of the root they are or are below, to the
     * other side of the root from the one [selectedNode] is on, so they all end up on the same side. Nothing
     * is moved when [selectedNode] is the root, and the root is in no branch to move.
     */
    fun moveNodeToOtherSide() {
        val node = mindMapManager.getSelectedNode()?.takeIf { it.parentId != null } ?: return
        val side = if (tree.sideOf(node.id) == LayoutSide.LEFT) LayoutSide.RIGHT else LayoutSide.LEFT
        selectedNodes
            .filter { it.parentId != null }
            .map { tree.branchOf(it.id).id }
            .distinct()
            .forEach { tree.setRootChildSide(it, side) }
        animateTreeChange()
    }

    /**
     * Folds the selected nodes, which hides the nodes below them, or unfolds them when there is none left of
     * them to fold. Only a node with children is folded, and the root never is.
     */
    fun toggleFold() {
        val nodes = selectedNodes.map { tree.getNode(it.id) }.filter { isFoldable(it) }
        if (nodes.isEmpty()) return
        val isFolding = nodes.any { !tree.isFolded(it.id) }
        changeFolds { nodes.forEach { tree.setFolded(it.id, isFolding) } }
    }

    /**
     * Folds the children of the selected node, or unfolds them when there is none left of them to fold.
     */
    fun toggleChildrenFold() {
        val children = (mindMapManager.getSelectedNode() ?: return).children.filter { tree.getNode(it).children.isNotEmpty() }
        val isFolding = children.any { !tree.isFolded(it) }
        changeFolds { children.forEach { tree.setFolded(it, isFolding) } }
    }

    /**
     * Folds the lowest of the nodes that are unfolded, of the selected node and the nodes below it: one
     * level of what is shown of them is hidden. The root is taken when no node is selected.
     */
    fun foldOneLevel() {
        val unfolded = foldBranch().filter { (node, _) -> isFoldable(node) && !tree.isFolded(node.id) }
        val depth = unfolded.maxOfOrNull { it.second } ?: return
        changeFolds { unfolded.forEach { (node, nodeDepth) -> if (nodeDepth == depth) tree.setFolded(node.id, true) } }
    }

    /**
     * Unfolds the highest of the nodes that are folded, of the selected node and the nodes shown below it:
     * one more level of them is shown. The root is taken when no node is selected.
     */
    fun unfoldOneLevel() {
        val folded = foldBranch(isVisibleOnly = true).filter { (node, _) -> tree.isFolded(node.id) }
        val depth = folded.minOfOrNull { it.second } ?: return
        changeFolds { folded.forEach { (node, nodeDepth) -> if (nodeDepth == depth) tree.setFolded(node.id, false) } }
    }

    /** Folds the selected node and every node below it. The root is taken when no node is selected. */
    fun foldAll() {
        changeFolds { foldBranch().forEach { (node, _) -> tree.setFolded(node.id, true) } }
    }

    /** Unfolds the selected node and every node below it. The root is taken when no node is selected. */
    fun unfoldAll() {
        changeFolds { foldBranch().forEach { (node, _) -> tree.setFolded(node.id, false) } }
    }

    private fun isFoldable(node: NodeData<*>): Boolean = node.parentId != null && node.children.isNotEmpty()

    // The selected node and the nodes below it, each with how far below it is.
    private fun foldBranch(isVisibleOnly: Boolean = false): List<Pair<NodeData<*>, Int>> {
        val nodes = mutableListOf<Pair<NodeData<*>, Int>>()
        fun traverse(
            node: NodeData<*>
            , depth: Int
        ) {
            nodes.add(node to depth)
            val children = if (isVisibleOnly) tree.visibleChildren(node) else node.children
            children.forEach { traverse(tree.getNode(it), depth + 1) }
        }
        traverse(mindMapManager.getSelectedNode()?.let { tree.getNode(it.id) } ?: tree.getRootNode(), 0)
        return nodes
    }

    // The folds are those of the selected node and the nodes below it, so none of them hides the selected node.
    private fun changeFolds(change: () -> Unit) {
        change()
        animateTreeChange()
    }

    fun editNodeText(description: String) {
        mindMapManager.getSelectedNode()?.let { node ->
            val updateNodeCommand = UpdateNodeCommand(mindMapManager, node, description)
            updateNodeCommand.execute()
            animateTreeChange()
        }
    }

    fun editNodeColor(color: Color) {
        mindMapManager.getSelectedNode()?.let { node ->
            tree.updateNodeColor(node.id, color)
            invalidate()
        }
    }

    /** Gives the selected node another shape. The root stays the circle it is. */
    fun editNodeShape(shape: NodeShape) {
        mindMapManager.getSelectedNode()?.let { node ->
            tree.updateNodeShape(node.id, shape)
            mindMapManager.update(tree)
            animateTreeChange()
        }
    }

    fun editNodeTextColor(textColor: Color) {
        mindMapManager.getSelectedNode()?.let { node ->
            tree.updateNodeTextColor(node.id, textColor)
            invalidate()
        }
    }

    /** Gives the selected node these attributes in place of the ones it has. */
    fun editNodeAttributes(attributes: List<NodeAttribute>) {
        mindMapManager.getSelectedNode()?.let { node ->
            tree.setAttributes(node.id, attributes)
            mindMapManager.update(tree)
            animateTreeChange()
        }
    }

    fun editNodeNote(note: String) {
        mindMapManager.getSelectedNode()?.let { node ->
            tree.updateNodeNote(node.id, note)
            invalidate()
        }
    }

    /**
     * Adds an arrow link from one node to another and returns it, or the one there already is between
     * them. Returns null when the two are not different nodes of the tree.
     */
    fun addArrowLink(
        sourceId: String
        , targetId: String
    ): ArrowLink? {
        if (sourceId == targetId || sourceId !in tree.nodes || targetId !in tree.nodes) return null
        tree.arrowLinks.firstOrNull { it.sourceId == sourceId && it.targetId == targetId }?.let { return it }
        val link = ArrowLink(IdGenerator.makeRandomNodeId(), sourceId, targetId)
        tree.addArrowLink(link)
        invalidate()
        return link
    }

    /** Replaces the arrow link with the id of [link], to change its color or its arrowheads. */
    fun updateArrowLink(link: ArrowLink) {
        if (tree.arrowLinks.none { it.id == link.id }) return
        tree.addArrowLink(link)
        invalidate()
    }

    fun removeArrowLink(id: String) {
        tree.removeArrowLink(id)
        invalidate()
    }

    /**
     * Selects a node and, when it is out of view, moves the mind map to bring it to the center. A node that
     * is hidden in a folded node is shown first, by unfolding the nodes above it.
     */
    fun goToNode(id: String) {
        if (id !in tree.nodes) return
        if (!tree.isVisible(id)) {
            var parentId = tree.getNode(id).parentId
            while (parentId != null) {
                tree.setFolded(parentId, false)
                parentId = tree.getNode(parentId).parentId
            }
            treeChangeJob?.cancel()
            mindMapManager.arrangeTree()
        }
        val node = tree.getNode(id)
        otherSelectedIds = emptyList()
        rangeAnchorId = id
        mindMapManager.setSelectedNode(node)
        val position =
            Offset(node.path.centerX.toPx(density), node.path.centerY.toPx(density)) * scaleFactor + currentPoint
        if (!viewportSize.isEmpty() && !Rect(Offset.Zero, viewportSize).contains(position)) {
            val moved = currentPoint + Offset(viewportSize.width / 2, viewportSize.height / 2) - position
            currentPoint = limitPan(moved)
        }
        invalidate()
    }

    fun fitScreen() {
        if (viewportSize.isEmpty()) return
        val root = tree.getRootNode()
        val widthScale =
            (mindMapManager.measureWidth(root).toPx(density) + Dp(50f).toPx(density)) / viewportSize.width
        val heightScale = maxOf(
            mindMapManager.measureHeight(root).toPx(density) * 1.4f, Dp(500f).toPx(density)
        ) / viewportSize.height
        val scale = maxOf(widthScale, heightScale, DEFAULT_ZOOM)
        executeAnimation(
            FitAnimation(
                scaleFactor,
                DEFAULT_ZOOM / scale,
                currentPoint.x,
                currentPoint.y
            ) { scale, x, y ->
                scaleFactor = scale
                currentPoint = Offset(x, y)
            }
        )
    }

    fun animateTreeChange() {
        treeChangeJob?.cancel()
        treeChangeJob = executeAnimation(TreeChangeAnimation(mindMapManager) { invalidate() })
        invalidate()
    }

    internal fun attach(measureTextSize: MeasureTextSize, density: Density, scope: CoroutineScope) {
        this.density = density
        this.scope = scope
        mindMapManager.setMeasureTextSize(measureTextSize)
        invalidate()
        val animations = pendingAnimations.toList()
        pendingAnimations.clear()
        animations.forEach { executeAnimation(it) }
    }

    internal fun detach() {
        scope = null
        mindMapManager.setMeasureTextSize(null)
    }

    internal fun onSizeChanged(size: Size) {
        if (size == viewportSize) return
        viewportSize = size
        if (tree.isRootPlaced) centerRootAgain() else placeRootIfNeeded()
    }

    // A touch that changes the selection in another way than selecting a node alone leaves it as it is
    // where there is no node.
    internal fun onTouchDown(
        position: Offset
        , selectionChange: SelectionChange = SelectionChange.SELECT
    ): NodeData<*>? {
        mindMapManager.setNotMoving()
        val touchedNode = findNode(toTreePoint(position))
        when {
            selectionChange == SelectionChange.SELECT -> select(touchedNode)
            touchedNode == null -> {}
            selectionChange == SelectionChange.RANGE -> selectRange(touchedNode.id)
        }
        return touchedNode
    }

    // A null position is a pointer that is not hovering: one that left the mind map or has a button pressed.
    internal fun onHover(position: Offset?) {
        if (position != null) hoverPosition = position
        hoveredNodeId = position?.let { findNode(toTreePoint(it))?.id }
    }

    internal fun findNodeAt(position: Offset): NodeData<*>? = findNode(toTreePoint(position))

    private fun findNode(point: Offset): NodeData<*>? {
        var foundNode: NodeData<*>? = null
        tree.doVisiblePreorderTraversal { node ->
            if (isInsideNode(node, point)) {
                foundNode = node
            }
        }
        return foundNode
    }

    // The last drawn of the arrow links near enough to the position: it is the one on top there.
    internal fun findArrowLink(position: Offset): ArrowLink? {
        val point = toTreePoint(position)
        val reach = ARROW_LINK_REACH.toPx(density) / scaleFactor
        return tree.arrowLinks.lastOrNull { link ->
            density.arrowLinkCurve(tree, link)?.let { it.distanceTo(point) <= reach } == true
        }
    }

    // The text of a node is drawn centered on the node, above its attributes when they are shown, so that
    // is where its links are looked for.
    internal fun findLink(position: Offset): Pair<NodeData<*>, String>? {
        val point = toTreePoint(position)
        val node = findNode(point) ?: return null
        val content =
            mindMapManager.getMeasureTextSize()?.measureContent(tree, node, showAttributes) ?: return null
        val layout = content.text
        val center = Offset(node.path.centerX.toPx(density), node.path.centerY.toPx(density))
        val textPoint =
            point - center + Offset(layout.size.width / 2f, layout.size.height / 2f + content.textRise)
        // The offset is that of the nearest gap between two characters, so the point is in one of the two.
        val gap = layout.getOffsetForPosition(textPoint)
        val text = layout.layoutInput.text
        return listOf(gap - 1, gap)
            .filter { it in text.indices && layout.getBoundingBox(it).contains(textPoint) }
            .firstNotNullOfOrNull { RichText.linkAt(text, it) }
            ?.let { node to it }
    }

    // The root has an add button for each of its sets of children; other nodes have one on their own side.
    internal fun addButtonSides(node: NodeData<*>): List<LayoutSide> {
        return if (node.parentId == null) LayoutSide.entries else listOf(tree.sideOf(node.id))
    }

    internal fun findAddButton(position: Offset): Pair<NodeData<*>, LayoutSide>? {
        val point = toTreePoint(position)
        val radius = ADD_BUTTON_RADIUS.toPx(density)
        var button: Pair<NodeData<*>, LayoutSide>? = null
        tree.doVisiblePreorderTraversal { node ->
            // A folded node has its fold marker there instead.
            if (tree.isFolded(node.id)) return@doVisiblePreorderTraversal
            addButtonSides(node).forEach { side ->
                if ((point - density.addButtonCenter(node, side)).getDistance() <= radius) {
                    button = node to side
                }
            }
        }
        return button
    }

    internal fun findFoldMarker(position: Offset): NodeData<*>? {
        val point = toTreePoint(position)
        val radius = ADD_BUTTON_RADIUS.toPx(density)
        var marked: NodeData<*>? = null
        tree.doVisiblePreorderTraversal { node ->
            if (tree.isFolded(node.id) && (point - density.foldMarkerCenter(tree, node)).getDistance() <= radius) {
                marked = node
            }
        }
        return marked
    }

    internal fun onFoldMarkerClick(node: NodeData<*>) {
        mindMapManager.setNotMoving()
        select(node)
        toggleFold()
    }

    // The menu of a node that is one of the selected nodes acts on all of them, so they stay selected.
    internal fun onNodeMenuClick(node: NodeData<*>) {
        if (selectedNodes.none { it.id == node.id }) {
            select(node)
        }
        else if (selectedNode?.id != node.id) {
            otherSelectedIds = otherSelectedIds - node.id + listOfNotNull(selectedNode?.id)
            mindMapManager.setSelectedNode(node)
            invalidate()
        }
    }

    internal fun onAddButtonClick(node: NodeData<*>) {
        mindMapManager.setNotMoving()
        select(node)
    }

    internal fun onNodeDrag(position: Offset) {
        val selectedNode = mindMapManager.getSelectedNode() ?: return
        val point = toTreePoint(position)
        // The root is attached to no other node, so it is not moving as they are: the tree goes along with it.
        if (selectedNode.parentId == null) {
            treeChangeJob?.cancel()
            tree.moveRoot(Dp(Px(point.x).toDp(density)), Dp(Px(point.y).toDp(density)))
            mindMapManager.arrangeTree()
            invalidate()
            return
        }
        mindMapManager.setMoving()
        treeChangeJob?.cancel()
        tree.updateNode(
            selectedNode.id,
            selectedNode.description,
            selectedNode.children,
            Dp(Px(point.x).toDp(density)),
            Dp(Px(point.y).toDp(density)),
        )
        mindMapManager.update(tree)
        mindMapManager.getLayoutManager(tree.sideOf(selectedNode.id)).arrangeNode(tree, tree.getNode(selectedNode.id))
        attachedNode = findIncludedNode(point, selectedNode.id)
        invalidate()
    }

    internal fun onTouchUp(onNodeMove: (tree: Tree<*>, target: NodeData<*>, parent: NodeData<*>) -> Unit) {
        if (mindMapManager.getMovingState()) {
            mindMapManager.setNotMoving()
            val selectedNode = mindMapManager.getSelectedNode()
            val attachedNode = this.attachedNode
            if (selectedNode != null && attachedNode != null) {
                // A node dropped on the root joins the children on the side of the root it was dropped on.
                val side =
                    if (selectedNode.path.centerX.dpVal < attachedNode.path.centerX.dpVal) LayoutSide.LEFT
                    else LayoutSide.RIGHT
                tree.removeNode(selectedNode.id)
                tree.attachNode(selectedNode.id, attachedNode.id, side)
                onNodeMove(tree, selectedNode, attachedNode)
            }
        }
        attachedNode = null
        animateTreeChange()
    }

    // An arrow link is dragged from the selected node, which stays where it is.
    internal fun onLinkDrag(position: Offset) {
        val selectedNode = mindMapManager.getSelectedNode() ?: return
        val point = toTreePoint(position)
        linkDragPoint = point
        attachedNode = findNode(point)?.takeIf { it.id != selectedNode.id }
        invalidate()
    }

    // Dropped on another node, the arrow link to it is added, or removed when there is one already.
    internal fun onLinkDrop(
        onNodeLink: (tree: Tree<*>, from: NodeData<*>, to: NodeData<*>, isLinked: Boolean) -> Unit,
    ) {
        val selectedNode = mindMapManager.getSelectedNode()
        val attachedNode = this.attachedNode
        if (linkDragPoint != null && selectedNode != null && attachedNode != null) {
            val link = tree.arrowLinks.firstOrNull { it.sourceId == selectedNode.id && it.targetId == attachedNode.id }
            if (link == null) tree.addArrowLink(ArrowLink(IdGenerator.makeRandomNodeId(), selectedNode.id, attachedNode.id))
            else tree.removeArrowLink(link.id)
            onNodeLink(tree, selectedNode, attachedNode, link == null)
        }
        linkDragPoint = null
        this.attachedNode = null
        invalidate()
    }

    internal fun onTransform(pan: Offset, zoom: Float) {
        scaleFactor *= zoom
        currentPoint = limitPan(currentPoint + pan)
    }

    // The mind map is not panned past its left and top, other than to the nodes a dragged root left there.
    private fun limitPan(point: Offset): Offset {
        val (overflowX, overflowY) = mindMapManager.measureOverflow()
        return Offset(
            minOf(overflowX.toPx(density) * scaleFactor, point.x),
            minOf(overflowY.toPx(density) * scaleFactor, point.y),
        )
    }

    private fun executeAnimation(strategy: AnimationStrategy): Job? {
        val scope = this.scope
        if (scope == null) {
            pendingAnimations.add(strategy)
            return null
        }
        return scope.launch {
            mindMapAnimator.setAnimationStrategy(strategy)
            mindMapAnimator.executeAnimation()
        }
    }

    private fun invalidate() {
        mindMapManager.getSelectedNode()?.let { selectedNode ->
            tree.nodes[selectedNode.id]?.let { mindMapManager.setSelectedNode(it) }
        }
        drawTick++
    }

    private fun placeRootIfNeeded() {
        if (tree.isRootPlaced || viewportSize.isEmpty()) return
        tree.placeRoot(
            Dp(Px(viewportSize.width).toDp(density) / 2),
            Dp(Px(viewportSize.height).toDp(density) / 2),
        )
        invalidate()
    }

    // The center is another one once the mind map has another size, and the nodes below the root go along.
    private fun centerRootAgain() {
        if (!tree.isRootCentered || viewportSize.isEmpty()) return
        treeChangeJob?.cancel()
        tree.placeRoot(
            Dp(Px(viewportSize.width).toDp(density) / 2),
            Dp(Px(viewportSize.height).toDp(density) / 2),
        )
        mindMapManager.arrangeTree()
        invalidate()
    }

    private fun toTreePoint(position: Offset): Offset {
        return (position - currentPoint) / scaleFactor
    }

    // The dragged node's own subtree is skipped: attaching a node below itself would detach it from the tree.
    private fun findIncludedNode(point: Offset, selectedNodeId: String): NodeData<*>? {
        var includedNode: NodeData<*>? = null

        fun traverse(node: NodeData<*>) {
            if (node.id == selectedNodeId) return
            if (isInsideNode(node, point)) {
                includedNode = node
            }
            tree.visibleChildren(node).forEach { childId -> traverse(tree.getNode(childId)) }
        }
        traverse(tree.getRootNode())
        return includedNode
    }

    private fun isInsideNode(
        node: NodeData<*>,
        point: Offset,
    ): Boolean {
        return when (node) {
            is CircleNodeData ->
                point.x in (node.path.centerX - node.path.radius).toPx(density)..(node.path.centerX + node.path.radius).toPx(density) &&
                    point.y in (node.path.centerY - node.path.radius).toPx(density)..(node.path.centerY + node.path.radius).toPx(density)

            is RectangleNodeData ->
                point.x in node.path.leftX().toPx(density)..node.path.rightX().toPx(density) &&
                    point.y in node.path.topY().toPx(density)..node.path.bottomY().toPx(density)

            is StadiumNodeData ->
                node.path.contains(Dp(Px(point.x).toDp(density)), Dp(Px(point.y).toDp(density)))

            is OvalNodeData ->
                node.path.contains(Dp(Px(point.x).toDp(density)), Dp(Px(point.y).toDp(density)))
        }
    }

    companion object {
        private const val DEFAULT_ZOOM = 1f
    }
}

// How a touch on a node changes the selected nodes: it selects the node alone, selects a range of nodes
// up to it, or leaves the selection to be changed once it is known whether the touch is a click or a drag.
internal enum class SelectionChange {
    SELECT,
    RANGE,
    LATER,
}

// What was copied: nodes with the nodes below them, and the arrow links between all of them.
private class CopiedBranch(
    val roots: List<CopiedNode>
    , val arrowLinks: List<ArrowLink>
)

// A copied node, which is pasted with the nodes below it and the attributes of each of them.
private class CopiedNode(
    val node: NodeData<*>
    , val attributes: List<NodeAttribute>
    , val children: List<CopiedNode>
)
