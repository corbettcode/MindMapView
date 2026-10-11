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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.mindsync.library.animator.AnimationStrategy
import com.mindsync.library.animator.FitAnimation
import com.mindsync.library.animator.MindMapAnimator
import com.mindsync.library.animator.TreeChangeAnimation
import com.mindsync.library.command.AddNodeCommand
import com.mindsync.library.command.RemoveNodeCommand
import com.mindsync.library.command.UpdateNodeCommand
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.layout.MindMapRightLayoutManager
import com.mindsync.library.util.Dp
import com.mindsync.library.util.NodeGenerator
import com.mindsync.library.util.Px
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
class MindMapState(tree: Tree<*> = Tree<Any>()) {
    val mindMapManager = MindMapManager()
    private val rightLayoutManager = MindMapRightLayoutManager()
    private val mindMapAnimator = MindMapAnimator()
    private val pendingAnimations = mutableListOf<AnimationStrategy>()
    private var scope: CoroutineScope? = null
    private var treeChangeJob: Job? = null
    private var density = Density(1f)
    private var viewportSize = Size.Zero
    private var addNode: NodeData<*>? = null

    var scaleFactor by mutableFloatStateOf(DEFAULT_ZOOM)
        private set
    var currentPoint by mutableStateOf(Offset.Zero)
        private set
    internal var attachedNode: NodeData<*>? by mutableStateOf(null)
        private set
    internal var drawTick by mutableIntStateOf(0)
        private set

    val tree: Tree<*> get() = mindMapManager.getTree()
    val selectedNode: NodeData<*>? get() = mindMapManager.getSelectedNode()

    init {
        mindMapManager.setTree(tree)
    }

    fun setTree(tree: Tree<*>) {
        mindMapManager.setTree(tree)
        placeRootIfNeeded()
        invalidate()
    }

    fun addNode(description: String, shape: NodeShape = NodeShape.RECTANGLE) {
        mindMapManager.getSelectedNode()?.let { node ->
            val addNode: NodeData<*> = when (shape) {
                NodeShape.RECTANGLE -> NodeGenerator.makeNode(RectangleNodeData::class, description, node.id)
                NodeShape.STADIUM -> NodeGenerator.makeNode(StadiumNodeData::class, description, node.id)
            }
            this.addNode = addNode
            val addNodeCommand = AddNodeCommand(mindMapManager, addNode)
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

    fun editNodeText(description: String) {
        mindMapManager.getSelectedNode()?.let { node ->
            val updateNodeCommand = UpdateNodeCommand(mindMapManager, node, description)
            updateNodeCommand.execute()
            animateTreeChange()
        }
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
        viewportSize = size
        placeRootIfNeeded()
    }

    internal fun onTouchDown(position: Offset): NodeData<*>? {
        mindMapManager.setNotMoving()
        val point = toTreePoint(position)
        var touchedNode: NodeData<*>? = null
        tree.doPreorderTraversal { node ->
            if (isInsideNode(node, point)) {
                touchedNode = node
            }
        }
        mindMapManager.setSelectedNode(touchedNode)
        invalidate()
        return touchedNode
    }

    internal fun onNodeDrag(position: Offset) {
        val selectedNode = mindMapManager.getSelectedNode()?.takeIf { it !is CircleNodeData } ?: return
        val point = toTreePoint(position)
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
        rightLayoutManager.arrangeNode(tree, tree.getNode(selectedNode.id))
        attachedNode = findIncludedNode(point, selectedNode.id)
        invalidate()
    }

    internal fun onTouchUp(onNodeMove: (tree: Tree<*>, target: NodeData<*>, parent: NodeData<*>) -> Unit) {
        if (mindMapManager.getMovingState()) {
            mindMapManager.setNotMoving()
            val selectedNode = mindMapManager.getSelectedNode()
            val attachedNode = this.attachedNode
            if (selectedNode != null && attachedNode != null) {
                tree.removeNode(selectedNode.id)
                tree.attachNode(selectedNode.id, attachedNode.id)
                onNodeMove(tree, selectedNode, attachedNode)
            }
        }
        attachedNode = null
        animateTreeChange()
    }

    internal fun onTransform(pan: Offset, zoom: Float) {
        scaleFactor *= zoom
        currentPoint = Offset(
            minOf(0f, currentPoint.x + pan.x),
            minOf(0f, currentPoint.y + pan.y),
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
            node.children.forEach { childId -> traverse(tree.getNode(childId)) }
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
        }
    }

    companion object {
        private const val DEFAULT_ZOOM = 1f
    }
}
