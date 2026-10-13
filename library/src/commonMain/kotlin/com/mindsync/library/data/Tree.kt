package com.mindsync.library.data

import androidx.compose.ui.graphics.Color
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.library.util.Dp
import com.mindsync.library.util.ExceptionMessage
import com.mindsync.library.util.NodeGenerator

class Tree<T> {
    private var _nodes: MutableMap<String, NodeData<*>>
    val nodes get() = _nodes.toMap()

    internal var isRootPlaced = true
        private set

    // A root that is given no position is kept in the center of the mind map, whatever size that takes.
    internal var isRootCentered = false
        private set

    // The root has a set of children for each side; these are the ones on its left.
    private val leftRootChildIds = mutableSetOf<String>()

    private val nodeAttributes = mutableMapOf<String, List<NodeAttribute>>()

    private val foldedIds = mutableSetOf<String>()

    private val _arrowLinks = mutableListOf<ArrowLink>()

    /** The arrow links between the nodes of the tree, in the order they are drawn. */
    val arrowLinks: List<ArrowLink> get() = _arrowLinks.toList()

    constructor() : this(Dp(0f), Dp(0f)) {
        isRootPlaced = false
        isRootCentered = true
    }

    constructor(
        rootCenterX: Dp
        , rootCenterY: Dp
    ) {
        _nodes = mutableMapOf()
        _nodes[ROOT_ID] =
            CircleNodeData(
                id = ROOT_ID
                , children = listOf()
                , color = Color.LightGray
                , description = DescriptionEmoji.prepend(ROOT_EMOJI, "root")
                , parentId = null
                , path =
                    CirclePath(
                        rootCenterX
                        , rootCenterY
                        , Dp(50f)
                    )
                , textColor = Color.Black
            ) as NodeData<T>
    }

    constructor(
        nodes: Map<String, NodeData<*>>
    ) {
        this._nodes = nodes.toMutableMap()
    }

    /**
     * Returns the side of the root the node is on: the side of the child of the root it is, or is below.
     * The root itself is on neither side, and [LayoutSide.RIGHT] is returned for it.
     */
    fun sideOf(id: String): LayoutSide {
        return if (branchOf(id).id in leftRootChildIds) LayoutSide.LEFT else LayoutSide.RIGHT
    }

    /**
     * Moves the branch the node is in, the child of the root it is or is below, to the other side of the
     * root. The root itself is in no branch, and nothing is moved for it.
     */
    fun moveToOtherSide(id: String) {
        val branch = branchOf(id)
        if (branch.parentId == null) return
        setRootChildSide(branch.id, if (sideOf(branch.id) == LayoutSide.LEFT) LayoutSide.RIGHT else LayoutSide.LEFT)
    }

    // The child of the root the node is, or is below; the root for the root itself.
    private fun branchOf(id: String): NodeData<*> {
        var node = getNode(id)
        while (node.parentId != null && node.parentId != ROOT_ID) {
            node = getNode(node.parentId)
        }
        return node
    }

    /**
     * Moves a child of the root, and the nodes below it, to [side] of the root.
     */
    fun setRootChildSide(
        childId: String
        , side: LayoutSide
    ) {
        if (side == LayoutSide.LEFT && _nodes[childId]?.parentId == ROOT_ID) {
            leftRootChildIds.add(childId)
        }
        else {
            leftRootChildIds.remove(childId)
        }
    }

    /** Returns the attributes of a node, in the order they are shown. */
    fun attributesOf(id: String): List<NodeAttribute> = nodeAttributes[id].orEmpty()

    fun setAttributes(
        id: String
        , attributes: List<NodeAttribute>
    ) {
        getNode(id)
        if (attributes.isEmpty()) nodeAttributes.remove(id) else nodeAttributes[id] = attributes.toList()
    }

    /** Whether the node is folded: the nodes below it are hidden, as in FreeMind. */
    fun isFolded(id: String): Boolean = id in foldedIds && id in _nodes

    /**
     * Folds or unfolds a node. Only a node with children is folded, and the root never is.
     */
    fun setFolded(
        id: String
        , isFolded: Boolean
    ) {
        val node = getNode(id)
        if (isFolded && node.parentId != null && node.children.isNotEmpty()) foldedIds.add(id) else foldedIds.remove(id)
    }

    /** Returns the children of the node that are shown: none of them when it is folded. */
    fun visibleChildren(node: NodeData<*>): List<String> = if (isFolded(node.id)) emptyList() else node.children

    /** Whether the node is shown: it is not when a node above it is folded. */
    fun isVisible(id: String): Boolean = id in _nodes && visibleAncestorOf(id) == id

    /**
     * Returns the node itself when it is shown, and otherwise the folded node above it that is: the one
     * it is hidden in.
     */
    fun visibleAncestorOf(id: String): String {
        var visibleId = id
        var parentId = getNode(id).parentId
        while (parentId != null) {
            if (isFolded(parentId)) visibleId = parentId
            parentId = getNode(parentId).parentId
        }
        return visibleId
    }

    /**
     * Adds an arrow link, in place of the one with its id when there is one. Its nodes have to be two
     * different nodes of the tree.
     */
    fun addArrowLink(link: ArrowLink) {
        if (link.sourceId == link.targetId) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
        getNode(link.sourceId)
        getNode(link.targetId)
        val index = _arrowLinks.indexOfFirst { it.id == link.id }
        if (index == -1) _arrowLinks.add(link) else _arrowLinks[index] = link
    }

    fun removeArrowLink(id: String) {
        _arrowLinks.removeAll { it.id == id }
    }

    /**
     * Removes the arrow links from and to the node and the nodes below it.
     */
    fun removeArrowLinksOfBranch(id: String) {
        val ids = mutableSetOf<String>()
        doPreorderTraversal(getNode(id)) { node -> ids.add(node.id) }
        _arrowLinks.removeAll { it.sourceId in ids || it.targetId in ids }
    }

    fun copy(nodes: Map<String, NodeData<T>>): Tree<T> {
        return Tree(nodes)
    }

    fun getRootNode(): CircleNodeData {
        _nodes[ROOT_ID]
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_ROOT_NODE_NOT_EXIST.message)
        return _nodes[ROOT_ID] as CircleNodeData
    }

    fun setRootNode(root: NodeData<*>) {
        _nodes[ROOT_ID] = root
    }

    internal fun placeRoot(centerX: Dp, centerY: Dp) {
        val root = getRootNode()
        _nodes[ROOT_ID] = root.copy(path = root.path.copy(centerX = centerX, centerY = centerY))
        isRootPlaced = true
    }

    fun setNode(
        id: String,
        nodeData: NodeData<*>,
    ) {
        _nodes[id] = nodeData
    }

    fun getNode(id: String?): NodeData<*> {
        return _nodes[id]
            ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_INVALID_NODE_ID.message)
    }

    fun addNode(
        targetId: String,
        parentId: String?,
        content: String,
        shape: NodeShape = NodeShape.RECTANGLE,
        color: Color = shape.defaultColor,
        textColor: Color = shape.defaultTextColor,
        side: LayoutSide = LayoutSide.RIGHT,
        note: String = "",
    ) {
        if (_nodes[targetId] != null) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_DUPLICATED_NODE.message)
        if (parentId == null) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message)
        val newNode: NodeData<*> =
            when (shape) {
                NodeShape.CIRCLE ->
                    NodeGenerator.makeNode(CircleNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.RECTANGLE ->
                    NodeGenerator.makeNode(RectangleNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.STADIUM ->
                    NodeGenerator.makeNode(StadiumNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
                NodeShape.OVAL ->
                    NodeGenerator.makeNode(OvalNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor, note = note)
            }
        val parentNode =
            _nodes[parentId] ?: throw IllegalArgumentException(
                ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message,
            )
        val newChildren = parentNode.children.toMutableList()
        newChildren.add(targetId)
        val newParent = when (parentNode) {
            is CircleNodeData -> parentNode.copy(children = newChildren)
            is RectangleNodeData -> parentNode.copy(children = newChildren)
            is StadiumNodeData -> parentNode.copy(children = newChildren)
            is OvalNodeData -> parentNode.copy(children = newChildren)
        }
        _nodes[parentId] = newParent as NodeData<T>
        _nodes[targetId] = newNode as NodeData<T>
        // A folded node would hide the child it is given.
        foldedIds.remove(parentId)
        setRootChildSide(targetId, side)
    }

    fun attachNode(
        targetId: String,
        parentId: String?,
        side: LayoutSide = LayoutSide.RIGHT,
    ) {
        if (targetId == ROOT_ID || parentId == null) return
        val targetNode = nodes[targetId] ?: return

        val newTargetNode =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(parentId = parentId)
                is RectangleNodeData -> targetNode.copy(parentId = parentId)
                is StadiumNodeData -> targetNode.copy(parentId = parentId)
                is OvalNodeData -> targetNode.copy(parentId = parentId)
            }
        val parentNode = nodes[parentId] ?: return
        val newNodes = parentNode.children.toMutableList()
        newNodes.add(targetId)
        val newParentNode =
            when (parentNode) {
                is CircleNodeData -> parentNode.copy(children = newNodes)
                is RectangleNodeData -> parentNode.copy(children = newNodes)
                is StadiumNodeData -> parentNode.copy(children = newNodes)
                is OvalNodeData -> parentNode.copy(children = newNodes)
            }
        _nodes[targetId] = newTargetNode as NodeData<T>
        _nodes[parentId] = newParentNode as NodeData<T>
        foldedIds.remove(parentId)
        setRootChildSide(targetId, side)
    }

    fun removeNode(targetId: String) {
        if (targetId == ROOT_ID) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_ROOT_CANT_REMOVE.message)
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        targetNode.parentId?.let { parentId ->
            val parentNode =
                _nodes[parentId] ?: throw IllegalArgumentException(
                    ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message,
                )
            val newChildren = parentNode.children.filter { id -> id != targetId }
            if (newChildren.isEmpty()) foldedIds.remove(parentId)
            val newParent =
                when (parentNode) {
                    is CircleNodeData -> parentNode.copy(children = newChildren)
                    is RectangleNodeData -> parentNode.copy(children = newChildren)
                    is StadiumNodeData -> parentNode.copy(children = newChildren)
                    is OvalNodeData -> parentNode.copy(children = newChildren)
                }
            _nodes[parentId] = newParent as NodeData<T>
        } ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_ROOT_CANT_REMOVE.message)
        leftRootChildIds.remove(targetId)
    }

    fun updateNode(
        targetId: String,
        description: String,
        children: List<String> = emptyList(),
        dx: Dp = Dp(0f),
        dy: Dp = Dp(0f),
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        val newTargetNode =
            when (targetNode) {
                is CircleNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is RectangleNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is StadiumNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
                is OvalNodeData ->
                    targetNode.copy(
                        path =
                            targetNode.path.copy(
                                centerX = dx,
                                centerY = dy,
                            ),
                        description = description,
                        children = if (children.isEmpty()) targetNode.children else children,
                    )
            }
        _nodes[targetId] = newTargetNode
    }

    fun updateNodeColor(
        targetId: String,
        color: Color,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        _nodes[targetId] =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(color = color)
                is RectangleNodeData -> targetNode.copy(color = color)
                is StadiumNodeData -> targetNode.copy(color = color)
                is OvalNodeData -> targetNode.copy(color = color)
            }
    }

    fun updateNodeNote(
        targetId: String,
        note: String,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        _nodes[targetId] =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(note = note)
                is RectangleNodeData -> targetNode.copy(note = note)
                is StadiumNodeData -> targetNode.copy(note = note)
                is OvalNodeData -> targetNode.copy(note = note)
            }
    }

    fun updateNodeTextColor(
        targetId: String,
        textColor: Color,
    ) {
        val targetNode =
            _nodes[targetId]
                ?: throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_TARGET_NODE_NOT_EXIST.message)
        _nodes[targetId] =
            when (targetNode) {
                is CircleNodeData -> targetNode.copy(textColor = textColor)
                is RectangleNodeData -> targetNode.copy(textColor = textColor)
                is StadiumNodeData -> targetNode.copy(textColor = textColor)
                is OvalNodeData -> targetNode.copy(textColor = textColor)
            }
    }

    fun doPreorderTraversal(
        nodeData: NodeData<*> = getRootNode(),
        action: (nodeData: NodeData<*>) -> Unit,
    ) {
        action.invoke(nodeData)
        nodeData.children.forEach { childId ->
            doPreorderTraversal(getNode(childId), action)
        }
    }

    fun doPreorderTraversal(
        nodeData: NodeData<*> = getRootNode(),
        depth: Int = 0,
        action: (nodeData: NodeData<*>, depth: Int) -> Unit,
    ) {
        action.invoke(nodeData, depth)
        nodeData.children.forEach { childId ->
            doPreorderTraversal(getNode(childId), depth + 1, action)
        }
    }

    /** Calls [action] for the nodes that are shown: those that are not below a folded node. */
    fun doVisiblePreorderTraversal(
        nodeData: NodeData<*> = getRootNode(),
        action: (nodeData: NodeData<*>) -> Unit,
    ) {
        action.invoke(nodeData)
        visibleChildren(nodeData).forEach { childId ->
            doVisiblePreorderTraversal(getNode(childId), action)
        }
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val ROOT_EMOJI = "🌱"
    }
}
