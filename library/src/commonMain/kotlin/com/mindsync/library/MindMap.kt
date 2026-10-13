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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import com.mindsync.library.data.ArrowLink
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

private const val ATTACH_CIRCLE_NODE_RANGE_VALUE = 15f
private const val SCROLL_ZOOM_STEP = 1.1f
private const val TEXT_CACHE_SIZE = 64
private const val ADD_BUTTON_ARM_RATIO = 0.5f
private const val ADD_BUTTON_STROKE_RATIO = 0.25f

private const val NOTE_MARKER_FOLD_RATIO = 0.4f
private const val NOTE_MARKER_STROKE_RATIO = 0.1f

// cos 45°: how far along each radius the top right of a round edge is.
private const val TOP_RIGHT_OF_ARC = 0.70710677f

internal val ADD_BUTTON_RADIUS = Dp(10f)
internal val NOTE_MARKER_SIZE = Dp(12f)

// How near to an arrow link a click on it has to be.
internal val ARROW_LINK_REACH = Dp(8f)
private val ARROW_LINK_WIDTH = Dp(2f)
private val ARROW_HEAD_LENGTH = Dp(12f)
private val ARROW_HEAD_HALF_WIDTH = Dp(5f)

/**
 * Draws the mind map held by [state]. The mind map fills the size given by [modifier], so pass a size
 * modifier such as `Modifier.fillMaxSize()`.
 *
 * When [onAddChildClick] is given, every node gets a '+' button in the color of the lines, drawn where
 * its lines to its children start. The root has one for each side, as it has a set of children on each.
 * Clicking a button selects its node and calls [onAddChildClick] with the node and the side of the
 * button, so [MindMapState.addNode] with that side then adds the child there.
 *
 * A click on a link in the text of a node, one that is not the start of a drag, calls [onLinkClick] with
 * the node and the URL of the link. The click selects the node as any other click on it does.
 *
 * A click on a node with the secondary mouse button selects the node and calls [onNodeMenu] with it and the
 * position of the click, for showing a menu there.
 *
 * The arrow links of the tree are drawn on top of its nodes. A click on one with the secondary mouse
 * button, where there is no node, calls [onArrowLinkMenu] with the link and the position of the click, for
 * showing a menu there.
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
    , onLinkClick: (node: NodeData<*>, url: String) -> Unit = { _, _ -> }
    , onArrowLinkMenu: (link: ArrowLink, position: Offset) -> Unit = { _, _ -> }
    , onNodeMenu: (node: NodeData<*>, position: Offset) -> Unit = { _, _ -> }
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
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnArrowLinkMenu by rememberUpdatedState(onArrowLinkMenu)
    val currentOnNodeMenu by rememberUpdatedState(onNodeMenu)

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
                        val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (dragStart != null) {
                            state.onNodeDrag(dragStart.position)
                            drag(dragStart.id) { change ->
                                state.onNodeDrag(change.position)
                                change.consume()
                            }
                        }
                        state.onTouchUp(currentOnNodeMove)
                        if (dragStart == null) {
                            state.findLink(down.position)?.let { (node, url) -> currentOnLinkClick(node, url) }
                        }
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
                        // The gestures above only follow the primary button, so the secondary one is seen here.
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val change = event.changes.firstOrNull()
                            val node = change?.let { state.findNodeAt(it.position) }
                            val link = change?.takeIf { node == null }?.let { state.findArrowLink(it.position) }
                            if (change != null && node != null) {
                                change.consume()
                                state.onNodeMenuClick(node)
                                currentOnNodeMenu(state.selectedNode ?: node, change.position)
                            }
                            else if (change != null && link != null) {
                                change.consume()
                                currentOnArrowLinkMenu(link, change.position)
                            }
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
            if (onAddChildClick != null) {
                drawAddButtons(state, drawInfo, lineColor, addIconColor)
            }
            drawTree(state, drawInfo, measure)
            drawArrowLinks(state)

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

private fun DrawScope.drawArrowLinks(state: MindMapState) {
    val tree = state.tree
    val path = Path()
    val stroke = Stroke(width = ARROW_LINK_WIDTH.toPx(this), cap = StrokeCap.Round)

    tree.arrowLinks.forEach { link ->
        val curve = arrowLinkCurve(tree, link) ?: return@forEach
        path.reset()
        path.moveTo(curve.start.x, curve.start.y)
        path.cubicTo(
            curve.startControl.x, curve.startControl.y, curve.endControl.x, curve.endControl.y, curve.end.x, curve.end.y,
        )
        drawPath(path, link.color, style = stroke)
        if (link.hasStartArrow) drawArrowHead(curve.start, curve.startControl, curve.end, link.color)
        if (link.hasEndArrow) drawArrowHead(curve.end, curve.endControl, curve.start, link.color)
    }
}

// The head points the way the curve arrives at its tip: away from the control point of that end, or from
// the other end when the control point is on the tip.
private fun DrawScope.drawArrowHead(
    tip: Offset
    , control: Offset
    , otherEnd: Offset
    , color: Color
) {
    val along = (tip - control).takeIf { it.getDistance() > 0f } ?: (tip - otherEnd)
    val length = along.getDistance()
    if (length == 0f) return
    val direction = along / length
    val across = Offset(-direction.y, direction.x) * ARROW_HEAD_HALF_WIDTH.toPx(this)
    val base = tip - direction * ARROW_HEAD_LENGTH.toPx(this)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + across.x, base.y + across.y)
        lineTo(base.x - across.x, base.y - across.y)
        close()
    }
    drawPath(head, color)
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
