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

    // The root has a set of children for each side; these are the ones on its left.
    private val leftRootChildIds = mutableSetOf<String>()

    constructor() : this(Dp(0f), Dp(0f)) {
        isRootPlaced = false
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
        var node = getNode(id)
        while (node.parentId != null && node.parentId != ROOT_ID) {
            node = getNode(node.parentId)
        }
        return if (node.id in leftRootChildIds) LayoutSide.LEFT else LayoutSide.RIGHT
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
    ) {
        if (_nodes[targetId] != null) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_DUPLICATED_NODE.message)
        if (parentId == null) throw IllegalArgumentException(ExceptionMessage.ERROR_MESSAGE_PARENT_NODE_NOT_EXIST.message)
        val newNode: NodeData<*> =
            when (shape) {
                NodeShape.CIRCLE ->
                    NodeGenerator.makeNode(CircleNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor)
                NodeShape.RECTANGLE ->
                    NodeGenerator.makeNode(RectangleNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor)
                NodeShape.STADIUM ->
                    NodeGenerator.makeNode(StadiumNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor)
                NodeShape.OVAL ->
                    NodeGenerator.makeNode(OvalNodeData::class, description = content, parentId = parentId)
                        .copy(id = targetId, parentId = parentId, color = color, textColor = textColor)
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

    companion object {
        private const val ROOT_ID = "root"
        private const val ROOT_EMOJI = "🌱"
    }
}
