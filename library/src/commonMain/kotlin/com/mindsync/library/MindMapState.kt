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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
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
import com.mindsync.library.data.ArrowLinkInclination
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeLine
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OutlineNode
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.LineNodeData
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.data.TreeSnapshot
import com.mindsync.library.data.shape
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.Direction
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.model.LevelStyle
import com.mindsync.library.model.MapTheme
import com.mindsync.library.model.isDarkBackground
import com.mindsync.library.model.NoteDisplay
import com.mindsync.library.model.TopicKind
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
import kotlin.math.abs

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

    private var noteMarkerNodeId by mutableStateOf<String?>(null)

    /**
     * The node whose note icon the mouse pointer is over while no button is pressed, or null when it is over none,
     * for showing its note. The icon is only drawn where the note is not shown inline.
     */
    val noteMarkerNode: NodeData<*>? get() = noteMarkerNodeId?.let { tree.nodes[it] }

    private var attributeMarkerNodeId by mutableStateOf<String?>(null)

    /**
     * The node whose attribute icon the mouse pointer is over while no button is pressed, or null when it is over
     * none, for showing its attributes. The icon is only drawn where the attributes are not shown inline.
     */
    val attributeMarkerNode: NodeData<*>? get() = attributeMarkerNodeId?.let { tree.nodes[it] }

    // The node whose add buttons are shown for the mouse pointer: the one it is over, and still the one whose add
    // buttons it went on to or is pressing.
    private var addButtonHoverNodeId by mutableStateOf<String?>(null)

    /**
     * Whether the mouse pointer is over an arrow link while no button is pressed. A node over the link is what a
     * click there goes to, so then it is not.
     */
    var isArrowLinkHovered by mutableStateOf(false)
        private set

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

    /**
     * Loads the image at an address in a node's text, or returns null when it cannot. Without it, no images are
     * shown in the nodes. Each image is loaded once, and the tree is arranged again when it is, as its node grows.
     */
    var imageLoader: (suspend (String) -> ImageBitmap?)? = null
        set(value) {
            field = value
            requestedImages.clear()
        }

    private val images = mutableMapOf<String, ImageBitmap>()
    private val requestedImages = mutableSetOf<String>()

    // An image that is not loaded yet is asked for, and takes no room until it is there.
    private fun imageOf(src: String): ImageBitmap? {
        images[src]?.let { return it }
        val loader = imageLoader ?: return null
        val currentScope = scope ?: return null
        if (requestedImages.add(src)) {
            currentScope.launch {
                val image = loader(src) ?: return@launch
                images[src] = image
                mindMapManager.update(tree)
                animateTreeChange()
            }
        }
        return null
    }

    private var mapAttributeDisplay by mutableStateOf(AttributeDisplay.INLINE)

    /**
     * How the attributes of the nodes are shown where a node has no way of its own, set with
     * [editNodeAttributeDisplay]. Inline, they are a table below the text of their node. Setting it rearranges the
     * tree, as the nodes change size.
     */
    var attributeDisplay: AttributeDisplay
        get() = mapAttributeDisplay
        set(value) {
            if (value == mapAttributeDisplay) return
            mapAttributeDisplay = value
            mindMapManager.attributeDisplay = value
            attributeMarkerNodeId = null
            animateTreeChange()
        }

    /**
     * Returns how the attributes of the node are shown: its own way, or that of its kind of topic, in
     * [topicAttributeDisplays], or else [attributeDisplay].
     */
    fun attributeDisplayOf(id: String): AttributeDisplay {
        // The tree is no state of its own, so reading the tick is what tells a reader of a change.
        drawTick
        return tree.shownAttributeDisplayOf(id, attributeDisplay)
    }

    /**
     * How the attributes of each kind of topic are shown where a node has no way of its own, as
     * [Tree.topicAttributeDisplays]. Like [topicStyles], it is kept for a map opened after it, and not undone.
     */
    var topicAttributeDisplays: Map<TopicKind, AttributeDisplay> = emptyMap()
        set(value) {
            if (value == field) return
            field = value
            tree.topicAttributeDisplays = value
            attributeMarkerNodeId = null
            animateTreeChange()
        }

    private var mapNoteDisplay by mutableStateOf(NoteDisplay.FLYOVER)

    /**
     * How the notes of the nodes are shown where a node has no way of its own, set with [editNodeNoteDisplay].
     * Inline, a note is a paragraph below the text of its node. Setting it rearranges the tree, as the nodes
     * change size.
     */
    var noteDisplay: NoteDisplay
        get() = mapNoteDisplay
        set(value) {
            if (value == mapNoteDisplay) return
            mapNoteDisplay = value
            mindMapManager.noteDisplay = value
            noteMarkerNodeId = null
            animateTreeChange()
        }

    /**
     * Returns how the note of the node is shown: its own way, or that of its kind of topic, in [topicNoteDisplays],
     * or else [noteDisplay].
     */
    fun noteDisplayOf(id: String): NoteDisplay {
        // The tree is no state of its own, so reading the tick is what tells a reader of a change.
        drawTick
        return tree.shownNoteDisplayOf(id, noteDisplay)
    }

    /**
     * How the notes of each kind of topic are shown where a node has no way of its own, as
     * [Tree.topicNoteDisplays]. Like [topicStyles], it is kept for a map opened after it, and not undone.
     */
    var topicNoteDisplays: Map<TopicKind, NoteDisplay> = emptyMap()
        set(value) {
            if (value == field) return
            field = value
            tree.topicNoteDisplays = value
            noteMarkerNodeId = null
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
            recordChange { tree.getRootNode().children.forEach { tree.setRootChildSide(it, value) } }
            animateTreeChange()
        }

    // A step of the history: the map as it was, and what the app kept with its theme then.
    private class Step(
        val tree: TreeSnapshot
        , val themeLook: Any?
    )

    private val history = UndoHistory<Step>()

    /**
     * What the app keeps along with the map's theme, such as the preferences a theme sets, for an undo or redo that
     * changes the theme to give back with it. The library only keeps it: an undo or redo that goes to another theme
     * sets it to what it was with that theme, and one that keeps the theme leaves it as it is. Setting it is no
     * change to the map.
     */
    var themeLook: Any? by mutableStateOf(null)

    // The history is no state of its own, so reading the tick is what tells a reader of a change.
    private var historyTick by mutableIntStateOf(0)

    private var isRecording = false

    // The root dragged by the user, as the tree was before it was, until the drag ends.
    private var rootDragStart: TreeSnapshot? = null

    // The arrow link being reshaped by the user, as it was when the drag started, until the drag ends.
    private var arrowLinkDrag: ArrowLinkDrag? = null

    /** Whether there is a change to the map to undo with [undo]. */
    val canUndo: Boolean
        get() {
            historyTick
            return history.canUndo
        }

    /** Whether there is a change that was undone to redo with [redo]. */
    val canRedo: Boolean
        get() {
            historyTick
            return history.canRedo
        }

    /**
     * Puts the map back as it was before the last change to it. The selected nodes that are still there stay
     * selected.
     */
    fun undo() = restore { history.undo(it) }

    /** Makes the last change that was undone again. */
    fun redo() = restore { history.redo(it) }

    // The map as it was when it was saved last, or shown when it was not saved since.
    private var savedSnapshot: TreeSnapshot? = null

    /**
     * Whether the map is another one than it was when [markSaved] was called last, or [setTree] when that was
     * later. It is what a file of it holds that counts, so where its nodes are is no change. Undoing the changes
     * since leaves it unmodified again.
     */
    val isModified: Boolean
        get() {
            historyTick
            return savedSnapshot?.hasSameContentAs(tree.snapshot()) == false
        }

    /** Takes the map as it is now as the one that is saved, which [isModified] tells changes from. */
    fun markSaved() {
        savedSnapshot = tree.snapshot()
        historyTick++
    }

    /** Makes the [changes] to the map one change, which [undo] undoes all at once. */
    fun groupChanges(changes: () -> Unit) = recordChange(changes)

    // A change within another one is part of it, so only the outermost one is recorded.
    private inline fun recordChange(change: () -> Unit) {
        if (isRecording) return change()
        val before = tree.snapshot()
        val lookBefore = themeLook
        isRecording = true
        try {
            change()
        }
        finally {
            isRecording = false
            if (!before.hasSameContentAs(tree.snapshot())) record(before, lookBefore)
        }
    }

    private fun record(
        before: TreeSnapshot
        , lookBefore: Any? = themeLook
    ) {
        history.record(Step(before, lookBefore))
        historyTick++
    }

    private fun restore(step: (current: Step) -> Step?) {
        val restored = step(Step(tree.snapshot(), themeLook)) ?: return
        val snapshot = restored.tree
        historyTick++
        treeChangeJob?.cancel()
        // What the app keeps with the theme only comes back along with the theme, so a change of it since that
        // was no change of the theme is kept.
        if (snapshot.theme != tree.theme) themeLook = restored.themeLook
        tree.restore(snapshot)
        val selectedIds = selectedNodes.map { it.id }.filter { it in tree.nodes }
        otherSelectedIds = selectedIds.dropLast(1)
        mindMapManager.setSelectedNode(selectedIds.lastOrNull()?.let { tree.getNode(it) })
        if (rangeAnchorId !in tree.nodes) rangeAnchorId = null
        mindMapManager.setTree(tree)
        animateTreeChange()
    }

    init {
        mindMapManager.setTree(tree)
        savedSnapshot = tree.snapshot()
    }

    /**
     * Shows another tree, which is taken as the one that is saved. What there was to undo and redo is of the tree
     * before it, and is forgotten.
     */
    fun setTree(tree: Tree<*>) {
        if (mindMapManager.getSelectedNode()?.id !in tree.nodes) mindMapManager.setSelectedNode(null)
        otherSelectedIds = emptyList()
        tree.topicStyles = topicStyles
        tree.topicNoteDisplays = topicNoteDisplays
        tree.topicAttributeDisplays = topicAttributeDisplays
        mindMapManager.setTree(tree)
        history.clear()
        savedSnapshot = tree.snapshot()
        historyTick++
        placeRootIfNeeded()
        invalidate()
    }

    /**
     * Puts the nodes of [tree] in place of those of the map, as one change: unlike [setTree], it can be undone, and
     * the map is modified until it is saved.
     */
    fun replaceTree(tree: Tree<*>) {
        recordChange {
            treeChangeJob?.cancel()
            // The nodes are replaced, not the look of the map.
            val theme = this.tree.theme
            this.tree.restore(tree.snapshot())
            this.tree.theme = theme
            val selectedIds = selectedNodes.map { it.id }.filter { it in this.tree.nodes }
            otherSelectedIds = selectedIds.dropLast(1)
            mindMapManager.setSelectedNode(selectedIds.lastOrNull()?.let { this.tree.getNode(it) })
            if (rangeAnchorId !in this.tree.nodes) rangeAnchorId = null
            mindMapManager.setTree(this.tree)
            placeRootIfNeeded()
            animateTreeChange()
        }
    }

    fun addNode(
        description: String,
        shape: NodeShape = NodeShape.RECTANGLE,
        color: Color = shape.defaultColor,
        textColor: Color = shape.defaultTextColor,
        side: LayoutSide = layoutSide,
        note: String = "",
        line: NodeLine = NodeLine(),
        border: NodeBorder = NodeBorder(),
        noteDisplay: NoteDisplay? = null,
        attributeDisplay: AttributeDisplay? = null,
    ) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                // Only the root has a set of children for each side; below it a child is on the side of its parent.
                val addSide = if (node.parentId == null) side else tree.sideOf(node.id)
                addChild(
                    node
                    , description
                    , shape
                    , color
                    , textColor
                    , addSide
                    , note
                    , line
                    , border
                    , noteDisplay
                    , attributeDisplay
                )
            }
            animateTreeChange()
        }
    }

    /**
     * Adds a node next to the selected node, as a child of the same parent on the same side of the root: right
     * after it, or right before it when [isAbove]. The root has no siblings, so for the root a child is added on
     * [side], as [addNode] adds it. The selected node stays selected, and [getAddNode] returns the added one.
     */
    fun addSiblingNode(
        description: String
        , shape: NodeShape = NodeShape.RECTANGLE
        , color: Color = shape.defaultColor
        , textColor: Color = shape.defaultTextColor
        , isAbove: Boolean = false
        , side: LayoutSide = layoutSide
        , note: String = ""
        , line: NodeLine = NodeLine()
        , border: NodeBorder = NodeBorder()
    ) {
        val node = mindMapManager.getSelectedNode() ?: return
        val parentId = node.parentId
        if (parentId == null) {
            addNode(description, shape, color, textColor, side, note, line, border)
            return
        }
        recordChange {
            val parent = tree.getNode(parentId)
            val index = parent.children.indexOf(node.id) + if (isAbove) 0 else 1
            addChild(
                parent
                , description
                , shape
                , color
                , textColor
                , tree.sideOf(node.id)
                , note
                , line
                , border
                , index = index
            )
            animateTreeChange()
        }
    }

    private fun addChild(
        parent: NodeData<*>
        , description: String
        , shape: NodeShape
        , color: Color
        , textColor: Color
        , side: LayoutSide
        , note: String
        , line: NodeLine
        , border: NodeBorder
        , noteDisplay: NoteDisplay? = null
        , attributeDisplay: AttributeDisplay? = null
        , index: Int? = null
    ) {
        val addNode: NodeData<*> = when (shape) {
            NodeShape.CIRCLE -> NodeGenerator.makeNode(CircleNodeData::class, description, parent.id)
                .copy(color = color, textColor = textColor, note = note)
            NodeShape.RECTANGLE -> NodeGenerator.makeNode(RectangleNodeData::class, description, parent.id)
                .copy(color = color, textColor = textColor, note = note)
            NodeShape.SQUARE -> NodeGenerator.makeNode(SquareNodeData::class, description, parent.id)
                .copy(color = color, textColor = textColor, note = note)
            NodeShape.LINE -> NodeGenerator.makeNode(LineNodeData::class, description, parent.id)
                .copy(color = color, textColor = textColor, note = note)
            NodeShape.STADIUM -> NodeGenerator.makeNode(StadiumNodeData::class, description, parent.id)
                .copy(color = color, textColor = textColor, note = note)
            NodeShape.OVAL -> NodeGenerator.makeNode(OvalNodeData::class, description, parent.id)
                .copy(color = color, textColor = textColor, note = note)
        }
        this.addNode = addNode
        AddNodeCommand(mindMapManager, addNode, side, index).execute()
        tree.setLine(addNode.id, line)
        tree.setBorder(addNode.id, border)
        if (noteDisplay != null || attributeDisplay != null) {
            tree.setNoteDisplay(addNode.id, noteDisplay)
            tree.setAttributeDisplay(addNode.id, attributeDisplay)
            // A note or attributes shown in the node make it larger, which it was not measured with.
            mindMapManager.update(tree)
        }
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
        VISIBLE_SIDE_ORDER.forEach { side ->
            tree.visibleChildren(root)
                .filter { tree.sideOf(it) == side }
                .forEach { branchId -> tree.doVisiblePreorderTraversal(tree.getNode(branchId)) { ids.add(it.id) } }
        }
        return ids
    }

    /** Removes the selected nodes and the nodes below them. The root is not removed. */
    fun removeNode() {
        recordChange {
            val nodes = selectedBranches.filter { it.parentId != null }
            if (nodes.isEmpty()) return
            nodes.forEach { RemoveNodeCommand(mindMapManager, it).execute() }
            otherSelectedIds = emptyList()
            animateTreeChange()
        }
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
        , line = tree.lineOf(node.id)
        , border = tree.borderOf(node.id)
        , noteDisplay = tree.noteDisplayOf(node.id)
        , attributeDisplay = tree.attributeDisplayOf(node.id)
        , children = node.children.map { copyBranch(tree.getNode(it)) }
    )

    /**
     * Adds what was copied with [copyNode] as the last child of the selected node, on [side] of the root
     * when that is the selected node. It can be pasted again and again, each time as nodes of their own.
     */
    fun pasteNode(side: LayoutSide = layoutSide) {
        recordChange {
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
                tree.setLine(id, branch.line)
                tree.setBorder(id, branch.border)
                tree.setNoteDisplay(id, branch.noteDisplay)
                tree.setAttributeDisplay(id, branch.attributeDisplay)
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
    }

    /**
     * Adds the [nodes] and the nodes below them as the last children of the selected node, on [side] of the
     * root when that is the selected node. Each of them is a [shape] in [color] with text in [textColor], and
     * has [border].
     */
    fun addNodes(
        nodes: List<OutlineNode>
        , shape: NodeShape = NodeShape.RECTANGLE
        , color: Color = shape.defaultColor
        , textColor: Color = shape.defaultTextColor
        , side: LayoutSide = layoutSide
        , border: NodeBorder = NodeBorder()
    ) {
        recordChange {
            val parent = mindMapManager.getSelectedNode() ?: return
            if (nodes.isEmpty()) return
            val addedIds = mutableListOf<String>()
            fun add(
                node: OutlineNode
                , parentId: String
            ) {
                val id = IdGenerator.makeRandomNodeId()
                tree.addNode(id, parentId, node.description, shape, color, textColor, side)
                tree.setBorder(id, border)
                addedIds.add(id)
                node.children.forEach { add(it, id) }
            }
            nodes.forEach { add(it, parent.id) }
            growOutOf(parent.id, addedIds)
        }
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
        recordChange {
            val node = mindMapManager.getSelectedNode()?.takeIf { it.parentId != null } ?: return
            val side = tree.sideOf(node.id).mirrored
            selectedNodes
                .filter { it.parentId != null }
                .map { tree.branchOf(it.id).id }
                .distinct()
                .forEach { tree.setRootChildSide(it, side) }
            animateTreeChange()
        }
    }

    /**
     * Moves the branches the selected nodes are in, the children of the root they are or are below, to the
     * next side of the root round from the one [selectedNode] is on, clockwise or counterclockwise, so they all
     * end up on the same side. Nothing is moved when [selectedNode] is the root, and the root is in no branch
     * to move.
     */
    fun moveNodeAround(isClockwise: Boolean) {
        recordChange {
            val node = mindMapManager.getSelectedNode()?.takeIf { it.parentId != null } ?: return
            val side = tree.sideOf(node.id).next(isClockwise)
            selectedNodes
                .filter { it.parentId != null }
                .map { tree.branchOf(it.id).id }
                .distinct()
                .forEach { tree.setRootChildSide(it, side) }
            animateTreeChange()
        }
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
        recordChange {
            change()
            animateTreeChange()
        }
    }

    fun editNodeText(description: String) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                val updateNodeCommand = UpdateNodeCommand(mindMapManager, node, description)
                updateNodeCommand.execute()
                animateTreeChange()
            }
        }
    }

    fun editNodeColor(color: Color) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.updateNodeColor(node.id, color)
                invalidate()
            }
        }
    }

    /** Gives the selected node these line settings of its own; the nodes below it take them unless they set theirs. */
    fun editNodeLine(line: NodeLine) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.setLine(node.id, line)
                invalidate()
            }
        }
    }

    /** Gives the selected node [border], or takes its border away with one whose style is none. */
    fun editNodeBorder(border: NodeBorder) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.setBorder(node.id, border)
                invalidate()
            }
        }
    }

    /**
     * Gives the selected node its own way of showing its note, or with null makes it follow [noteDisplay]
     * again. The node is measured again, as a note shown inline is in it.
     */
    fun editNodeNoteDisplay(noteDisplay: NoteDisplay?) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.setNoteDisplay(node.id, noteDisplay)
                mindMapManager.update(tree)
                animateTreeChange()
            }
        }
    }

    /**
     * Gives the selected node its own way of showing its attributes, or with null makes it follow
     * [attributeDisplay] again. The node is measured again, as attributes shown inline are in it.
     */
    fun editNodeAttributeDisplay(attributeDisplay: AttributeDisplay?) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.setAttributeDisplay(node.id, attributeDisplay)
                mindMapManager.update(tree)
                animateTreeChange()
            }
        }
    }

    /** The look of the map, read as state, so what shows it is redrawn when it changes or is undone. */
    val theme: MapTheme
        get() {
            drawTick
            return tree.theme
        }

    /**
     * The color the map is drawn on in place of its theme's background, or null for the theme's. It is the app's
     * choice, not the map's, so it is not saved with the map and a change of it is not undone.
     */
    var backgroundColor: Color? by mutableStateOf(null)

    /** The color the map is drawn on: [backgroundColor], or the background of its theme when there is none. */
    val background: Color
        get() = backgroundColor ?: theme.background

    /** Whether the map is drawn on a dark [background], for what is drawn on it to be light. */
    val isDark: Boolean
        get() = isDarkBackground(background)

    /**
     * How the app draws each kind of topic, in place of the theme's style for its level, as [Tree.topicStyles]. It
     * is kept for a map that is opened or started after it, and a change of it is not undone.
     */
    var topicStyles: Map<TopicKind, LevelStyle> = emptyMap()
        set(value) {
            if (value == field) return
            field = value
            tree.topicStyles = value
            invalidate()
        }

    /** Gives the map another look, as one change. The colors and borders nodes were given by hand stay. */
    fun setTheme(theme: MapTheme) {
        if (theme == tree.theme) return
        recordChange {
            tree.theme = theme
            invalidate()
        }
    }

    /** Gives the selected node another shape. The root stays the circle it is. */
    fun editNodeShape(shape: NodeShape) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.updateNodeShape(node.id, shape)
                mindMapManager.update(tree)
                animateTreeChange()
            }
        }
    }

    fun editNodeTextColor(textColor: Color) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.updateNodeTextColor(node.id, textColor)
                invalidate()
            }
        }
    }

    /** Gives the selected node these attributes in place of the ones it has. */
    fun editNodeAttributes(attributes: List<NodeAttribute>) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.setAttributes(node.id, attributes)
                mindMapManager.update(tree)
                animateTreeChange()
            }
        }
    }

    fun editNodeNote(note: String) {
        recordChange {
            mindMapManager.getSelectedNode()?.let { node ->
                tree.updateNodeNote(node.id, note)
                // A note that is shown is in the node, which is then measured again to fit it.
                if (noteDisplayOf(node.id) == NoteDisplay.INLINE) {
                    mindMapManager.update(tree)
                    animateTreeChange()
                } else {
                    invalidate()
                }
            }
        }
    }

    /**
     * The color a new arrow link is drawn in, by [addArrowLink] or a drag with Ctrl held. A link that is pasted
     * keeps the color it had.
     */
    var newArrowLinkColor: Color = ArrowLink.DEFAULT_COLOR

    /**
     * Adds an arrow link from one node to another, in [newArrowLinkColor], and returns it, or the one there already
     * is between them. Returns null when the two are not different nodes of the tree.
     */
    fun addArrowLink(
        sourceId: String
        , targetId: String
    ): ArrowLink? {
        if (sourceId == targetId || sourceId !in tree.nodes || targetId !in tree.nodes) return null
        tree.arrowLinks.firstOrNull { it.sourceId == sourceId && it.targetId == targetId }?.let { return it }
        val link = ArrowLink(IdGenerator.makeRandomNodeId(), sourceId, targetId, color = newArrowLinkColor)
        recordChange { tree.addArrowLink(link) }
        invalidate()
        return link
    }

    /** Replaces the arrow link with the id of [link], to change its color or its arrowheads. */
    fun updateArrowLink(link: ArrowLink) {
        recordChange {
            if (tree.arrowLinks.none { it.id == link.id }) return
            tree.addArrowLink(link)
            invalidate()
        }
    }

    fun removeArrowLink(id: String) {
        recordChange {
            tree.removeArrowLink(id)
            invalidate()
        }
    }

    /**
     * Returns the whole mind map as an image, whatever part of it is in view: the nodes that are shown, their
     * lines and their arrow links, in the colors of a light theme on white. It is [pixelsPerDp], or smaller when
     * its largest side would be more than [maxSide] pixels. Returns null until the mind map is shown, as only then
     * are its nodes measured and arranged.
     */
    fun exportImage(
        pixelsPerDp: Float = 2f
        , maxSide: Int = 16_000
    ): ImageBitmap? {
        val measureTextSize = mindMapManager.getMeasureTextSize() ?: return null
        if (!tree.isRootPlaced) return null
        return mapImage(this, density, measureTextSize, pixelsPerDp, maxSide)
    }

    /**
     * Returns the whole mind map as an SVG document, whatever part of it is in view, drawn as [exportImage] draws
     * it: the nodes that are shown, their lines and their arrow links, in the colors of a light theme on white, at
     * one pixel a dp. Returns null until the mind map is shown, and on Android, where Compose does not draw with
     * Skia.
     */
    fun exportSvg(): String? {
        val measureTextSize = mindMapManager.getMeasureTextSize() ?: return null
        if (!tree.isRootPlaced) return null
        return mapSvg(this, density, measureTextSize)
    }

    /**
     * Returns the nodes whose description or note holds [text], ignoring case, in the order of the tree: each
     * node before the nodes below it. The text they show is searched, not their markup. Nodes hidden in a
     * folded node are found too, and [goToNode] shows them. Blank text finds none.
     */
    fun findNodes(text: String): List<NodeData<*>> {
        // The tree is no state of its own, so reading the tick is what tells a reader of a change.
        drawTick
        if (text.isBlank()) return emptyList()
        val found = mutableListOf<NodeData<*>>()
        tree.doPreorderTraversal { node ->
            val isMatch = listOf(node.description, node.note).any { html ->
                html.isNotEmpty() && RichText.parse(html).text.contains(text, ignoreCase = true)
            }
            if (isMatch) found.add(node)
        }
        return found
    }

    /**
     * Selects the parent of the selected node, one node up its branch towards the root. Nothing changes when
     * the root or no node is selected.
     */
    fun selectParent() {
        val parentId = selectedNode?.parentId ?: return
        goToNode(parentId)
    }

    /**
     * Selects the first child of the selected node, one node down its branch. Nothing changes when the root or
     * no node is selected, nor when the selected node has no children shown: none, or folded ones.
     */
    fun selectFirstChild() {
        val node = selectedNode?.takeIf { it.parentId != null } ?: return
        val childId = tree.visibleChildren(tree.getNode(node.id)).firstOrNull() ?: return
        goToNode(childId)
    }

    /**
     * Selects the next child of the parent of the selected node after it, or the one before it, as they are
     * listed, which is from the top down. Nothing changes at the end of the list, nor for a child of the root,
     * whose children are in sets around it rather than in one list.
     */
    fun selectSibling(isNext: Boolean) {
        val parentId = selectedNode?.parentId?.takeIf { it != tree.getRootNode().id } ?: return
        val siblings = tree.getNode(parentId).children
        val index = siblings.indexOf(selectedNode?.id) + if (isNext) 1 else -1
        siblings.getOrNull(index)?.let { goToNode(it) }
    }

    /**
     * Selects the nearest shown node that lies in [direction] from the selected node on the screen, as the arrow
     * keys do. A node counts when its center is past the edge of the selected node on that side; of those, one
     * level with the selected node is preferred over a nearer one off to the side. Nothing changes when there is
     * none, nor when no node is selected.
     */
    fun selectNodeInDirection(direction: Direction) {
        val selected = selectedNode ?: return
        val target = tree.nodes.values
            .filter { it.id != selected.id && tree.isVisible(it.id) }
            .mapNotNull { node -> directionScore(selected, node, direction)?.let { node to it } }
            .minByOrNull { it.second }
            ?.first
            ?: return
        goToNode(target.id)
    }

    /**
     * How far [node] is from [from] going in [direction], with distance off to the side counting
     * [SIDEWAYS_WEIGHT] times as much, or null when it is not that way.
     */
    private fun directionScore(
        from: NodeData<*>
        , node: NodeData<*>
        , direction: Direction
    ): Float? {
        val isHorizontal = direction.x != 0f
        val sign = if (isHorizontal) direction.x else direction.y
        val along = sign * if (isHorizontal) {
            (node.path.centerX - from.path.centerX).dpVal
        } else {
            (node.path.centerY - from.path.centerY).dpVal
        }
        val fromHalfAlong = (if (isHorizontal) halfWidth(from) else halfHeight(from)).dpVal
        if (along <= fromHalfAlong) return null
        val acrossCenters = abs(
            if (isHorizontal) (node.path.centerY - from.path.centerY).dpVal
            else (node.path.centerX - from.path.centerX).dpVal
        )
        val halfAcross = if (isHorizontal) {
            (halfHeight(node) + halfHeight(from)).dpVal
        } else {
            (halfWidth(node) + halfWidth(from)).dpVal
        }
        val sideways = (acrossCenters - halfAcross).coerceAtLeast(0f)
        return along + SIDEWAYS_WEIGHT * sideways
    }

    /**
     * Selects a node and, when it is out of view, moves the mind map to bring it to the center. A node that
     * is hidden in a folded node is shown first, by unfolding the nodes above it.
     */
    fun goToNode(id: String) {
        if (id !in tree.nodes) return
        if (!tree.isVisible(id)) {
            recordChange {
                var parentId = tree.getNode(id).parentId
                while (parentId != null) {
                    tree.setFolded(parentId, false)
                    parentId = tree.getNode(parentId).parentId
                }
            }
            treeChangeJob?.cancel()
            mindMapManager.arrangeTree()
        }
        val node = tree.getNode(id)
        otherSelectedIds = emptyList()
        rangeAnchorId = id
        mindMapManager.setSelectedNode(node)
        if (!viewportSize.isEmpty() && !Rect(Offset.Zero, viewportSize).contains(screenPosition(node))) {
            currentPoint = limitPan(panToCenter(node))
        }
        invalidate()
    }

    /**
     * Moves the view so the node is in the middle of it. The view goes no further past the top and left of
     * the tree than it can be panned, so a node near them may stay short of the middle.
     */
    fun centerNode(id: String) {
        val node = tree.nodes[id] ?: return
        if (viewportSize.isEmpty()) return
        currentPoint = limitPan(panToCenter(node))
        invalidate()
    }

    private fun screenPosition(
        node: NodeData<*>
    ): Offset = Offset(node.path.centerX.toPx(density), node.path.centerY.toPx(density)) * scaleFactor + currentPoint

    private fun panToCenter(
        node: NodeData<*>
    ): Offset = currentPoint + Offset(viewportSize.width / 2, viewportSize.height / 2) - screenPosition(node)

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
        measureTextSize.imageOf = ::imageOf
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
        noteMarkerNodeId = position?.let { findNoteMarker(toTreePoint(it))?.id }
        attributeMarkerNodeId = position?.let { findAttributeMarker(toTreePoint(it))?.id }
        isArrowLinkHovered = position != null && hoveredNodeId == null && findArrowLink(position) != null
        if (position != null) {
            val point = toTreePoint(position)
            addButtonHoverNodeId = hoveredNodeId
                ?: addButtonHoverNodeId?.takeIf { id -> tree.nodes[id]?.let { isOnAddButton(it, point) } == true }
        }
    }

    internal fun onPointerExit() {
        onHover(null)
        addButtonHoverNodeId = null
    }

    /**
     * The nodes whose add buttons are shown: the selected ones and the one the mouse pointer is over. A folded
     * node has its fold marker there instead.
     */
    internal fun addButtonNodes(): List<NodeData<*>> {
        val ids = (selectedNodes.map { it.id } + listOfNotNull(addButtonHoverNodeId)).toSet()
        return ids
            .mapNotNull { tree.nodes[it] }
            .filter { tree.isVisible(it.id) && !tree.isFolded(it.id) }
    }

    private fun isOnAddButton(
        node: NodeData<*>
        , point: Offset
    ): Boolean {
        val radius = ADD_BUTTON_RADIUS.toPx(density)
        return addButtonSides(node).any { side -> (point - density.addButtonCenter(node, side)).getDistance() <= radius }
    }

    internal fun findNodeAt(position: Offset): NodeData<*>? = findNode(toTreePoint(position))

    // The last drawn of the note icons at the point, as it is the one on top there.
    private fun findNoteMarker(
        point: Offset
    ): NodeData<*>? {
        val reach = NOTE_MARKER_SIZE.toPx(density) / 2
        var foundNode: NodeData<*>? = null
        tree.doVisiblePreorderTraversal { node ->
            val distance = point - density.noteMarkerCenter(node)
            val isOnMarker = abs(distance.x) <= reach && abs(distance.y) <= reach
            val hasMarker = node.note.isNotBlank()
                && tree.shownNoteDisplayOf(node.id, noteDisplay) != NoteDisplay.INLINE
            if (isOnMarker && hasMarker) foundNode = node
        }
        return foundNode
    }

    // The last drawn of the attribute icons at the point, as it is the one on top there.
    private fun findAttributeMarker(
        point: Offset
    ): NodeData<*>? {
        val reach = NOTE_MARKER_SIZE.toPx(density) / 2
        var foundNode: NodeData<*>? = null
        tree.doVisiblePreorderTraversal { node ->
            val distance = point - density.attributeMarkerCenter(node)
            val isOnMarker = abs(distance.x) <= reach && abs(distance.y) <= reach
            val hasMarker = tree.attributesOf(node.id).isNotEmpty()
                && tree.shownAttributeDisplayOf(node.id, attributeDisplay) != AttributeDisplay.INLINE
            if (isOnMarker && hasMarker) foundNode = node
        }
        return foundNode
    }

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
            mindMapManager.getMeasureTextSize()?.measureContent(tree, node, attributeDisplay, noteDisplay) ?: return null
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
        addButtonNodes().forEach { node ->
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
            if (rootDragStart == null) rootDragStart = tree.snapshot()
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
                // A node dropped on the root joins the children on the side of the root it was dropped nearest to.
                val side = LayoutSide.nearest(
                    (selectedNode.path.centerX - attachedNode.path.centerX).dpVal
                    , (selectedNode.path.centerY - attachedNode.path.centerY).dpVal
                )
                recordChange {
                    tree.removeNode(selectedNode.id)
                    tree.attachNode(selectedNode.id, attachedNode.id, side)
                }
                onNodeMove(tree, selectedNode, attachedNode)
            }
            // A node dropped on no other node takes the place among its siblings that it was dropped at.
            else if (selectedNode != null) {
                recordChange { tree.moveAmongSiblings(selectedNode.id, tree.getNode(selectedNode.id).path.centerY) }
            }
        }
        // Where the root is is not part of what is compared for a change, so the drag is recorded on its own.
        rootDragStart?.let { start ->
            if (start.nodes[tree.getRootNode().id]?.path != tree.getRootNode().path) record(start)
        }
        rootDragStart = null
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
            recordChange {
                if (link == null) {
                    tree.addArrowLink(
                        ArrowLink(
                            IdGenerator.makeRandomNodeId()
                            , selectedNode.id
                            , attachedNode.id
                            , color = newArrowLinkColor
                        )
                    )
                }
                else {
                    tree.removeArrowLink(link.id)
                }
            }
            onNodeLink(tree, selectedNode, attachedNode, link == null)
        }
        linkDragPoint = null
        this.attachedNode = null
        invalidate()
    }

    // The link is reshaped from where it is grabbed: the further along the curve, the more its end's control
    // point follows the pointer.
    internal fun onArrowLinkDragStart(
        link: ArrowLink
        , position: Offset
    ) {
        val curve = density.arrowLinkCurve(tree, link) ?: return
        val grabbed = toTreePoint(position)
        val t = curve.nearestT(grabbed).coerceIn(MIN_GRAB_T, 1 - MIN_GRAB_T)
        arrowLinkDrag = ArrowLinkDrag(
            linkId = link.id
            , before = tree.snapshot()
            , startInclination = curve.startControl - curve.start
            , endInclination = curve.endControl - curve.end
            , grabbed = grabbed
            , t = t
        )
    }

    // Moving both control points by the least that moves the grabbed point of the curve with the pointer.
    internal fun onArrowLinkDrag(position: Offset) {
        val drag = arrowLinkDrag ?: return
        val link = tree.arrowLinks.firstOrNull { it.id == drag.linkId } ?: return
        val moved = toTreePoint(position) - drag.grabbed
        val u = 1 - drag.t
        val startWeight = 3 * u * u * drag.t
        val endWeight = 3 * u * drag.t * drag.t
        val norm = startWeight * startWeight + endWeight * endWeight
        tree.addArrowLink(
            link.copy(
                startInclination = toInclination(drag.startInclination + moved * (startWeight / norm))
                , endInclination = toInclination(drag.endInclination + moved * (endWeight / norm))
            )
        )
        invalidate()
    }

    internal fun onArrowLinkDrop() {
        val drag = arrowLinkDrag ?: return
        arrowLinkDrag = null
        if (!drag.before.hasSameContentAs(tree.snapshot())) record(drag.before)
        invalidate()
    }

    private fun toInclination(offset: Offset): ArrowLinkInclination =
        ArrowLinkInclination(Dp(Px(offset.x).toDp(density)), Dp(Px(offset.y).toDp(density)))

    // The point of the map at the center of the screen stays there while zooming.
    internal fun onTransform(pan: Offset, zoom: Float) {
        val center = viewportSize.center
        scaleFactor *= zoom
        currentPoint = limitPan(center - (center - currentPoint) * zoom + pan)
    }

    // The mind map is not panned past its left and top, other than to the nodes a dragged root left there, or as far as
    // zooming out around the center of the screen moved it.
    private fun limitPan(point: Offset): Offset {
        val (overflowX, overflowY) = mindMapManager.measureOverflow()
        val zoomedOut = viewportSize.center * maxOf(0f, 1f - scaleFactor)
        return Offset(
            minOf(maxOf(overflowX.toPx(density) * scaleFactor, zoomedOut.x), point.x),
            minOf(maxOf(overflowY.toPx(density) * scaleFactor, zoomedOut.y), point.y),
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
            is SquareNodeData ->
                point.x in node.path.leftX().toPx(density)..node.path.rightX().toPx(density) &&
                    point.y in node.path.topY().toPx(density)..node.path.bottomY().toPx(density)
            is LineNodeData ->
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

        // The right sides come first, as they did when the root had only a left and a right side, each top down.
        private val VISIBLE_SIDE_ORDER = listOf(
            LayoutSide.UPPER_RIGHT
            , LayoutSide.RIGHT
            , LayoutSide.LOWER_RIGHT
            , LayoutSide.UPPER_LEFT
            , LayoutSide.LEFT
            , LayoutSide.LOWER_LEFT
        )
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

// A copied node, which is pasted with the nodes below it and the attributes and line settings of each of them.
private class CopiedNode(
    val node: NodeData<*>
    , val attributes: List<NodeAttribute>
    , val line: NodeLine
    , val border: NodeBorder
    , val noteDisplay: NoteDisplay?
    , val attributeDisplay: AttributeDisplay?
    , val children: List<CopiedNode>
)

// A node off to the side of an arrow key's way counts this many times as far as one straight ahead.
private const val SIDEWAYS_WEIGHT = 3f

// Near an end of the curve the control points barely move it, so a grab there is taken as a little further in.
private const val MIN_GRAB_T = 0.15f

private class ArrowLinkDrag(
    val linkId: String
    , val before: TreeSnapshot
    , val startInclination: Offset
    , val endInclination: Offset
    , val grabbed: Offset
    , val t: Float
)
