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
    val selectedNode: NodeData<*>? get() = mindMapManager.getSelectedNode()

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

    fun removeNode() {
        mindMapManager.getSelectedNode()?.let { node ->
            val removeNodeCommand = RemoveNodeCommand(mindMapManager, node)
            removeNodeCommand.execute()
            animateTreeChange()
        }
    }

    /** Whether there is a node that was copied with [copyNode] to paste with [pasteNode]. */
    val hasCopiedNode: Boolean get() = copiedBranch != null

    /**
     * Copies the selected node and the nodes below it, with their attributes and the arrow links between
     * them, as they are now: a node that is changed or removed afterwards is pasted as it was when it was
     * copied. An arrow link to or from a node that was not copied is left out, as it has no end to paste.
     */
    fun copyNode() {
        val node = mindMapManager.getSelectedNode() ?: return
        val root = copyBranch(tree.getNode(node.id))
        val ids = mutableSetOf<String>()
        tree.doPreorderTraversal(tree.getNode(node.id)) { ids.add(it.id) }
        copiedBranch = CopiedBranch(root, tree.arrowLinks.filter { it.sourceId in ids && it.targetId in ids })
    }

    /** Copies the selected node as [copyNode] does, then removes it. The root is neither copied nor removed. */
    fun cutNode() {
        if (mindMapManager.getSelectedNode()?.parentId == null) return
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
        paste(copied.root, parent.id)
        copied.arrowLinks.forEach { link ->
            tree.addArrowLink(
                link.copy(
                    id = IdGenerator.makeRandomNodeId()
                    , sourceId = pastedIds.getValue(link.sourceId)
                    , targetId = pastedIds.getValue(link.targetId)
                )
            )
        }
        // The pasted nodes come out of the node they are pasted into, as an added node does.
        val parentPath = tree.getNode(parent.id).path
        pastedIds.values.forEach { id ->
            val node = tree.getNode(id)
            tree.updateNode(id, node.description, node.children, parentPath.centerX, parentPath.centerY)
        }
        mindMapManager.update(tree)
        animateTreeChange()
    }

    /**
     * Moves the branch the selected node is in, the child of the root it is or is below, to the other
     * side of the root. Nothing is moved when the root is selected.
     */
    fun moveNodeToOtherSide() {
        mindMapManager.getSelectedNode()?.takeIf { it.parentId != null }?.let { node ->
            tree.moveToOtherSide(node.id)
            animateTreeChange()
        }
    }

    /**
     * Folds the selected node, which hides the nodes below it, or unfolds it when it is folded. Only a node
     * with children is folded, and the root never is.
     */
    fun toggleFold() {
        val node = mindMapManager.getSelectedNode() ?: return
        changeFolds { tree.setFolded(node.id, !tree.isFolded(node.id)) }
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

    internal fun onTouchDown(position: Offset): NodeData<*>? {
        mindMapManager.setNotMoving()
        val touchedNode = findNode(toTreePoint(position))
        mindMapManager.setSelectedNode(touchedNode)
        invalidate()
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
        mindMapManager.setSelectedNode(node)
        toggleFold()
    }

    internal fun onNodeMenuClick(node: NodeData<*>) {
        mindMapManager.setSelectedNode(node)
        invalidate()
    }

    internal fun onAddButtonClick(node: NodeData<*>) {
        mindMapManager.setNotMoving()
        mindMapManager.setSelectedNode(node)
        invalidate()
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

// What was copied: a node with the nodes below it, and the arrow links between them.
private class CopiedBranch(
    val root: CopiedNode
    , val arrowLinks: List<ArrowLink>
)

// A copied node, which is pasted with the nodes below it and the attributes of each of them.
private class CopiedNode(
    val node: NodeData<*>
    , val attributes: List<NodeAttribute>
    , val children: List<CopiedNode>
)
