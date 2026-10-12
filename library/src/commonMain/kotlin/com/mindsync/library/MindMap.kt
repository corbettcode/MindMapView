package com.mindsync.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.node.NodeDrawerFactory
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx
import kotlin.math.abs
import kotlin.math.sqrt

private const val ATTACH_CIRCLE_NODE_RANGE_VALUE = 15f
private const val SCROLL_ZOOM_STEP = 1.1f
private const val TEXT_CACHE_SIZE = 64
private const val ADD_BUTTON_ARM_RATIO = 0.5f
private const val ADD_BUTTON_STROKE_RATIO = 0.25f

private const val NOTE_MARKER_FOLD_RATIO = 0.4f
private const val NOTE_MARKER_STROKE_RATIO = 0.1f

// cos 45°: how far along each radius the top right of a round edge is.
private const val TOP_RIGHT_OF_ARC = 0.70710677f

// How far a link bows to the side of the straight line between its nodes, as a part of the length of that line.
private const val LINK_BOW_RATIO = 0.2f
private const val LINK_ARROW_WIDTH_RATIO = 0.4f

internal val ADD_BUTTON_RADIUS = Dp(10f)
private val LINK_ARROW_LENGTH = Dp(12f)
private val LINK_DRAG_DASH = Dp(6f)
internal val NOTE_MARKER_SIZE = Dp(12f)

/**
 * Draws the mind map held by [state]. The mind map fills the size given by [modifier], so pass a size
 * modifier such as `Modifier.fillMaxSize()`.
 *
 * When [onAddChildClick] is given, every node gets a '+' button in the color of the lines, drawn where
 * its lines to its children start. The root has one for each side, as it has a set of children on each.
 * Clicking a button selects its node and calls [onAddChildClick] with the node and the side of the
 * button, so [MindMapState.addNode] with that side then adds the child there.
 *
 * Dragging a node onto another while Ctrl is held down links the two with a curved arrow from the first
 * to the second instead of moving it, or removes that link when there is one already. [onNodeLink] is
 * then called with whether the nodes are linked now. See [MindMapState.addLink].
 *
 * A node with a note has a marker on its top right edge. The note itself is not drawn:
 * [MindMapState.hoveredNode] is the node under the mouse pointer, for showing its note.
 */
@Composable
fun MindMap(
    state: MindMapState
    , modifier: Modifier = Modifier
    , fontFamily: FontFamily? = null
    , onNodeClick: (node: NodeData<*>?) -> Unit = {}
    , onNodeDoubleClick: (node: NodeData<*>) -> Unit = {}
    , onNodeMove: (tree: Tree<*>, target: NodeData<*>, parent: NodeData<*>) -> Unit = { _, _, _ -> }
    , onAddChildClick: ((node: NodeData<*>, side: LayoutSide) -> Unit)? = null
    , onNodeLink: (tree: Tree<*>, from: NodeData<*>, to: NodeData<*>, isLinked: Boolean) -> Unit = { _, _, _, _ -> }
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer(cacheSize = TEXT_CACHE_SIZE)
    val drawInfo = remember { DrawInfo() }
    val measureTextSize = remember(textMeasurer, density, fontFamily) {
        val textStyle = TextStyle(
            fontSize = with(density) { drawInfo.textSize.dpVal.dp.toSp() },
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        )
        MeasureTextSize(textMeasurer, textStyle, density, drawInfo)
    }
    val isDarkTheme = isSystemInDarkTheme()
    val lineColor = if (isDarkTheme) drawInfo.darkModeLineColor else drawInfo.lineColor
    val addIconColor = if (isDarkTheme) Color.Black else Color.White
    val currentOnNodeClick by rememberUpdatedState(onNodeClick)
    val currentOnNodeDoubleClick by rememberUpdatedState(onNodeDoubleClick)
    val currentOnNodeMove by rememberUpdatedState(onNodeMove)
    val currentOnAddChildClick by rememberUpdatedState(onAddChildClick)
    val currentOnNodeLink by rememberUpdatedState(onNodeLink)

    DisposableEffect(state, measureTextSize, density, scope) {
        state.attach(measureTextSize, density, scope)
        onDispose { state.detach() }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { state.onSizeChanged(it.toSize()) }
            .pointerInput(state) {
                var lastClickedNodeId: String? = null
                var lastClickTime = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val addButton =
                        if (currentOnAddChildClick != null) state.findAddButton(down.position) else null
                    if (addButton != null) {
                        down.consume()
                        lastClickedNodeId = null
                        waitForUpOrCancellation()?.let { up ->
                            up.consume()
                            state.onAddButtonClick(addButton.first)
                            currentOnAddChildClick?.invoke(addButton.first, addButton.second)
                        }
                        return@awaitEachGesture
                    }
                    val touchedNode = state.onTouchDown(down.position)
                    currentOnNodeClick(touchedNode)
                    if (touchedNode == null) {
                        lastClickedNodeId = null
                        do {
                            val event = awaitPointerEvent()
                            state.onTransform(event.calculatePan(), event.calculateZoom())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                    else {
                        down.consume()
                        // With Ctrl held down a drag links the node to the one it is dropped on instead of moving it.
                        val isLinkDrag = currentEvent.keyboardModifiers.isCtrlPressed
                        val onDrag: (Offset) -> Unit = if (isLinkDrag) state::onLinkDrag else state::onNodeDrag
                        val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (dragStart != null) {
                            onDrag(dragStart.position)
                            drag(dragStart.id) { change ->
                                onDrag(change.position)
                                change.consume()
                            }
                        }
                        if (isLinkDrag) state.onLinkDrop(currentOnNodeLink) else state.onTouchUp(currentOnNodeMove)
                        // A second click on the same node shortly after the first is a double click; a drag is not a click.
                        val isSecondClick = lastClickedNodeId == touchedNode.id &&
                            down.uptimeMillis - lastClickTime <= viewConfiguration.doubleTapTimeoutMillis
                        if (dragStart != null || isSecondClick) {
                            lastClickedNodeId = null
                        }
                        else {
                            lastClickedNodeId = touchedNode.id
                            lastClickTime = currentEvent.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                        }
                        if (dragStart == null && isSecondClick) {
                            currentOnNodeDoubleClick(state.selectedNode ?: touchedNode)
                        }
                    }
                }
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter ->
                                state.onHover(event.changes.firstOrNull()?.takeIf { !it.pressed }?.position)
                            PointerEventType.Press, PointerEventType.Exit -> state.onHover(null)
                        }
                        if (event.type == PointerEventType.Scroll) {
                            val scrollY = event.changes.fold(0f) { sum, change -> sum + change.scrollDelta.y }
                            if (scrollY != 0f) {
                                state.onTransform(
                                    Offset.Zero,
                                    if (scrollY < 0f) SCROLL_ZOOM_STEP else 1f / SCROLL_ZOOM_STEP,
                                )
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
    ) {
        state.drawTick
        val measure = state.mindMapManager.getMeasureTextSize() ?: return@Canvas
        withTransform({
            translate(state.currentPoint.x, state.currentPoint.y)
            scale(state.scaleFactor, state.scaleFactor, Offset.Zero)
        }) {
            drawAttachedNode(state, drawInfo)
            drawLines(state, drawInfo, lineColor)
            drawLinks(state, drawInfo)
            if (onAddChildClick != null) {
                drawAddButtons(state, drawInfo, lineColor, addIconColor)
            }
            drawTree(state, drawInfo, measure)

            state.selectedNode?.let { selectedNode ->
                NodeDrawerFactory(
                    selectedNode
                    , drawInfo
                    , this
                )
                .createStrokeNode()
                .drawNode(this)
            }
        }
    }
}

private fun DrawScope.drawTree(
    state: MindMapState,
    drawInfo: DrawInfo,
    measureTextSize: MeasureTextSize,
) {
    state.tree.doPreorderTraversal { node ->
        val nodeDrawer = NodeDrawerFactory(node, drawInfo, this).createNodeDrawer()
        nodeDrawer.drawNode(this)
        nodeDrawer.drawText(this, measureTextSize.measure(node.description))
        if (node.note.isNotBlank()) drawNoteMarker(noteMarkerCenter(node), drawInfo)
    }
}

// A sheet of paper with its top right corner folded over.
private fun DrawScope.drawNoteMarker(
    center: Offset
    , drawInfo: DrawInfo
) {
    val half = NOTE_MARKER_SIZE.toPx(this) / 2
    val fold = half * 2 * NOTE_MARKER_FOLD_RATIO
    val stroke = Stroke(width = half * 2 * NOTE_MARKER_STROKE_RATIO)
    val left = center.x - half
    val top = center.y - half
    val right = center.x + half
    val bottom = center.y + half

    val sheet = Path().apply {
        moveTo(left, top)
        lineTo(right - fold, top)
        lineTo(right, top + fold)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }
    drawPath(sheet, drawInfo.noteMarkerColor)
    drawPath(sheet, drawInfo.noteMarkerLineColor, style = stroke)
    val foldedCorner = Path().apply {
        moveTo(right - fold, top)
        lineTo(right - fold, top + fold)
        lineTo(right, top + fold)
    }
    drawPath(foldedCorner, drawInfo.noteMarkerLineColor, style = stroke)
}

// The marker is centered on the top right of the node's edge, so it is clear of the node's text.
internal fun Density.noteMarkerCenter(node: NodeData<*>): Offset {
    val offsetX: Dp
    val offsetY: Dp
    when (node) {
        is CircleNodeData -> {
            offsetX = node.path.radius * TOP_RIGHT_OF_ARC
            offsetY = node.path.radius * TOP_RIGHT_OF_ARC
        }
        is RectangleNodeData -> {
            offsetX = node.path.width / 2
            offsetY = node.path.height / 2
        }
        is StadiumNodeData -> {
            offsetX = node.path.length / 2 + node.path.radius * TOP_RIGHT_OF_ARC
            offsetY = node.path.radius * TOP_RIGHT_OF_ARC
        }
        is OvalNodeData -> {
            offsetX = node.path.radiusX * TOP_RIGHT_OF_ARC
            offsetY = node.path.radiusY * TOP_RIGHT_OF_ARC
        }
    }
    return Offset((node.path.centerX + offsetX).toPx(this), (node.path.centerY - offsetY).toPx(this))
}

private fun DrawScope.drawAttachedNode(
    state: MindMapState
    , drawInfo: DrawInfo
) {
    state.attachedNode?.let { attachedNode ->
        val height = when (attachedNode) {
            is RectangleNodeData -> attachedNode.path.height
            is StadiumNodeData -> attachedNode.path.height()
            is OvalNodeData -> attachedNode.path.height()
            is CircleNodeData -> attachedNode.path.radius + Dp(ATTACH_CIRCLE_NODE_RANGE_VALUE)
        }
        val width = when (attachedNode) {
            is RectangleNodeData -> attachedNode.path.width
            is StadiumNodeData -> attachedNode.path.width()
            is OvalNodeData -> attachedNode.path.width()
            is CircleNodeData -> attachedNode.path.radius + Dp(ATTACH_CIRCLE_NODE_RANGE_VALUE)
        }
        drawCircle(
            color = drawInfo.boundaryColor,
            radius = maxOf(height.toPx(this), width.toPx(this)),
            center = Offset(attachedNode.path.centerX.toPx(this), attachedNode.path.centerY.toPx(this)),
        )
    }
}

/**
 * Draws the lines connecting the nodes in the mind map held by [state]. The mind map fills the
 * size given by [modifier], so pass a size modifier such as `Modifier.fillMaxSize()`.
 */
private fun DrawScope.drawLines(
    state: MindMapState
    , drawInfo: DrawInfo
    , lineColor: Color
) {
    val tree = state.tree
    val manager = state.mindMapManager
    val path = Path()
    val stroke = Stroke(width = drawInfo.lineWidth.toPx(this))

    forEachNodeWithLineColor(tree, drawInfo, lineColor) { toNode, color ->
        val node = toNode.parentId?.let { tree.getNode(it) } ?: return@forEachNodeWithLineColor
        if (manager.getMovingState().not() || manager.getSelectedNode()?.id != toNode.id) {
            val isRightSide = tree.sideOf(toNode.id) == LayoutSide.RIGHT
            val startX = getNodeEdgeX(node, isRightEdge = isRightSide)
            val startY = node.path.centerY.toPx(this)

            val endX = getNodeEdgeX(toNode, isRightEdge = isRightSide.not())
            val endY = toNode.path.centerY.toPx(this)

            val midX = (startX + endX) / 2

            path.reset()
            path.moveTo(startX, startY)
            path.cubicTo(midX, startY, midX, endY, endX, endY)

            drawPath(
                path = path
                , color = color
                , style = stroke
            )
        }
    }
}

internal class LinkCurve(
    val start: Offset
    , val control: Offset
    , val end: Offset
)

/**
 * The curve of a link: from the edge of [from] to the edge of [to], bowing to the left of that direction, so
 * the links in either direction between two nodes are apart.
 */
internal fun Density.linkCurve(
    from: NodeData<*>
    , to: NodeData<*>
): LinkCurve {
    val fromCenter = Offset(from.path.centerX.toPx(this), from.path.centerY.toPx(this))
    val toCenter = Offset(to.path.centerX.toPx(this), to.path.centerY.toPx(this))
    val direction = toCenter - fromCenter
    val control = (fromCenter + toCenter) / 2f + Offset(direction.y, -direction.x) * LINK_BOW_RATIO
    return LinkCurve(edgePoint(from, control), control, edgePoint(to, control))
}

// Where the straight line from the center of the node towards a point leaves the node.
private fun Density.edgePoint(
    node: NodeData<*>
    , towards: Offset
): Offset {
    val center = Offset(node.path.centerX.toPx(this), node.path.centerY.toPx(this))
    val direction = towards - center
    if (direction == Offset.Zero) return center
    val halfWidth: Dp
    val halfHeight: Dp
    val isRound: Boolean
    when (node) {
        is CircleNodeData -> {
            halfWidth = node.path.radius
            halfHeight = node.path.radius
            isRound = true
        }
        is OvalNodeData -> {
            halfWidth = node.path.radiusX
            halfHeight = node.path.radiusY
            isRound = true
        }
        is RectangleNodeData -> {
            halfWidth = node.path.width / 2
            halfHeight = node.path.height / 2
            isRound = false
        }
        // The caps of a stadium are left out: the line leaves the box around it.
        is StadiumNodeData -> {
            halfWidth = node.path.width() / 2
            halfHeight = node.path.height() / 2
            isRound = false
        }
    }
    val x = direction.x / halfWidth.toPx(this)
    val y = direction.y / halfHeight.toPx(this)
    val scale = if (isRound) sqrt(x * x + y * y) else maxOf(abs(x), abs(y))
    return if (scale == 0f || scale.isNaN() || scale.isInfinite()) center else center + direction / scale
}

private fun DrawScope.drawLinks(
    state: MindMapState
    , drawInfo: DrawInfo
) {
    val tree = state.tree
    val nodes = tree.nodes
    val stroke = Stroke(width = drawInfo.linkWidth.toPx(this))
    val path = Path()

    tree.links.forEach { link ->
        val from = nodes[link.fromId] ?: return@forEach
        val to = nodes[link.toId] ?: return@forEach
        val curve = linkCurve(from, to)
        path.reset()
        path.moveTo(curve.start.x, curve.start.y)
        path.quadraticTo(curve.control.x, curve.control.y, curve.end.x, curve.end.y)
        drawPath(path, drawInfo.linkColor, style = stroke)
        drawArrowHead(curve.end, curve.end - curve.control, drawInfo)
    }

    // The link being dragged, from the node it starts at to the pointer.
    val dragPoint = state.linkDragPoint ?: return
    val from = state.selectedNode ?: return
    val dash = LINK_DRAG_DASH.toPx(this)
    drawLine(
        color = drawInfo.linkColor
        , start = edgePoint(from, dragPoint)
        , end = dragPoint
        , strokeWidth = stroke.width
        , pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
    )
}

private fun DrawScope.drawArrowHead(
    tip: Offset
    , direction: Offset
    , drawInfo: DrawInfo
) {
    val length = direction.getDistance()
    if (length == 0f) return
    val along = direction / length * LINK_ARROW_LENGTH.toPx(this)
    val across = Offset(-along.y, along.x) * LINK_ARROW_WIDTH_RATIO
    val base = tip - along
    val arrowHead = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + across.x, base.y + across.y)
        lineTo(base.x - across.x, base.y - across.y)
        close()
    }
    drawPath(arrowHead, drawInfo.linkColor)
}

/**
 * Calls [action] for every node with the color of the lines of its branch: each child of the root starts
 * a branch with its own color, which the nodes below it share. The root is in no branch and gets
 * [lineColor].
 */
private fun forEachNodeWithLineColor(
    tree: Tree<*>
    , drawInfo: DrawInfo
    , lineColor: Color
    , action: (node: NodeData<*>, color: Color) -> Unit
) {
    fun traverse(
        node: NodeData<*>
        , color: Color
    ) {
        action(node, color)
        node.children.forEachIndexed { index, childId ->
            traverse(tree.getNode(childId), if (node.parentId == null) rootLineColor(drawInfo, index) else color)
        }
    }
    traverse(tree.getRootNode(), lineColor)
}

internal fun rootLineColor(
    drawInfo: DrawInfo
    , childIndex: Int
): Color {
    return drawInfo.rootLineColors[childIndex % drawInfo.rootLineColors.size]
}

private fun DrawScope.drawAddButtons(
    state: MindMapState
    , drawInfo: DrawInfo
    , lineColor: Color
    , iconColor: Color
) {
    val radius = ADD_BUTTON_RADIUS.toPx(this)
    val arm = radius * ADD_BUTTON_ARM_RATIO
    val strokeWidth = radius * ADD_BUTTON_STROKE_RATIO

    forEachNodeWithLineColor(state.tree, drawInfo, lineColor) { node, color ->
        state.addButtonSides(node).forEach { side ->
            val center = addButtonCenter(node, side)
            drawCircle(color = color, radius = radius, center = center)
            drawLine(iconColor, center - Offset(arm, 0f), center + Offset(arm, 0f), strokeWidth)
            drawLine(iconColor, center - Offset(0f, arm), center + Offset(0f, arm), strokeWidth)
        }
    }
}

// The button touches the node's edge where its lines start, so it covers the start of the lines, not the node.
internal fun Density.addButtonCenter(
    node: NodeData<*>
    , side: LayoutSide = LayoutSide.RIGHT
): Offset {
    val radius = ADD_BUTTON_RADIUS.toPx(this)
    val centerX = when (side) {
        LayoutSide.RIGHT -> getNodeEdgeX(node, isRightEdge = true) + radius
        LayoutSide.LEFT -> getNodeEdgeX(node, isRightEdge = false) - radius
    }
    return Offset(centerX, node.path.centerY.toPx(this))
}

private fun Density.getNodeEdgeX(
    node: NodeData<*>,
    isRightEdge: Boolean,
): Float {
    val nodeCenterX = node.path.centerX.toPx(this)
    val widthOffset =
        when (node) {
            is CircleNodeData -> node.path.radius.toPx(this)
            is RectangleNodeData -> node.path.width.toPx(this) / 2
            is StadiumNodeData -> node.path.width().toPx(this) / 2
            is OvalNodeData -> node.path.width().toPx(this) / 2
        }
    return if (isRightEdge) nodeCenterX + widthOffset else nodeCenterX - widthOffset
}
