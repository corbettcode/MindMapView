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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import com.mindsync.library.data.SquareNodeData
import com.mindsync.library.data.StadiumNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.layout.AttributeTable
import com.mindsync.library.layout.MeasureTextSize
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.node.NodeDrawerFactory
import com.mindsync.library.util.Dp
import com.mindsync.library.util.toPx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt
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

internal val ADD_BUTTON_RADIUS = Dp(6f)

// How finely the edge of the root is measured to space its add buttons evenly along it.
private const val ROOT_EDGE_STEPS = 360

// Half the spacing between the root and its children: how far out of the root a line to a slanted side turns.
private val SLANTED_LINE_BEND = Dp(25f)
private val LINK_DRAG_DASH = Dp(6f)
internal val NOTE_MARKER_SIZE = Dp(12f)
private val FOLD_MARKER_WIDTH = Dp(2f)

// How near to an arrow link a click on it has to be.
internal val ARROW_LINK_REACH = Dp(8f)
private val ARROW_LINK_WIDTH = Dp(2f)
private val ARROW_HEAD_LENGTH = Dp(12f)
private val ARROW_HEAD_HALF_WIDTH = Dp(5f)

private val ATTRIBUTE_LINE_WIDTH = Dp(1f)
private const val ATTRIBUTE_LINE_ALPHA = 0.5f

/**
 * Draws the mind map held by [state]. The mind map fills the size given by [modifier], so pass a size
 * modifier such as `Modifier.fillMaxSize()`.
 *
 * When [onAddChildClick] is given, the selected nodes and the node under the mouse pointer get a '+' button in
 * the color of the lines, drawn where their lines to their children start. The button stays while the pointer
 * moves on from the node to it. The root has one for each side, as it has a set of children on each.
 * Clicking a button selects its node and calls [onAddChildClick] with the node and the side of the
 * button, so [MindMapState.addNode] with that side then adds the child there.
 *
 * A click on a node while Ctrl is held down adds it to [MindMapState.selectedNodes], or takes it out of
 * them when it is one of them, instead of selecting it alone; a click with Ctrl on a link selects the node
 * alone, as a click on a link does. A click while Shift is held down selects the range of nodes up to the
 * node, see [MindMapState.selectRange]; it is not the start of a drag.
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
 * The attributes of a node are drawn as a table below its text while [MindMapState.showAttributes] is set.
 *
 * Dragging a node onto another while Ctrl is held down adds an arrow link from the first to the second
 * instead of moving it, or removes that arrow link when there is one already. [onNodeLink] is then called
 * with whether the nodes are linked now. See [MindMapState.addArrowLink].
 *
 * Dragging the root moves it and the tree with it, and the mind map pans to the nodes that are then above
 * or to the left of it. A root that is given no position is kept in the center of the mind map until it is
 * dragged.
 *
 * A folded node, whose nodes below it are hidden, has a ring where its lines to them would start, in place
 * of its '+' button. A click on the ring unfolds the node. See [MindMapState.toggleFold].
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
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnArrowLinkMenu by rememberUpdatedState(onArrowLinkMenu)
    val currentOnNodeMenu by rememberUpdatedState(onNodeMenu)
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
                    val foldMarkerNode = state.findFoldMarker(down.position)
                    if (foldMarkerNode != null) {
                        down.consume()
                        lastClickedNodeId = null
                        waitForUpOrCancellation()?.let { up ->
                            up.consume()
                            state.onFoldMarkerClick(foldMarkerNode)
                        }
                        return@awaitEachGesture
                    }
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
                    // With Shift held down a click selects a range of nodes. With Ctrl it adds the node to the
                    // selected nodes and centers it, or takes it out of them, unless it is a drag or a click on a link.
                    val isRange = currentEvent.keyboardModifiers.isShiftPressed
                    val isCtrl = currentEvent.keyboardModifiers.isCtrlPressed
                    val selectionChange = when {
                        isRange -> SelectionChange.RANGE
                        isCtrl -> SelectionChange.LATER
                        else -> SelectionChange.SELECT
                    }
                    val touchedNode = state.onTouchDown(down.position, selectionChange)
                    currentOnNodeClick(touchedNode)
                    if (touchedNode != null && isRange) {
                        down.consume()
                        lastClickedNodeId = null
                        waitForUpOrCancellation()?.consume()
                    }
                    else if (touchedNode == null) {
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
                        val isLinkDrag = isCtrl
                        val onDrag: (Offset) -> Unit = if (isLinkDrag) state::onLinkDrag else state::onNodeDrag
                        val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        val link = if (dragStart == null) state.findLink(down.position) else null
                        if (isCtrl) {
                            if (dragStart == null && link == null) {
                                state.toggleSelected(touchedNode.id)
                                if (state.selectedNode?.id == touchedNode.id) state.centerNode(touchedNode.id)
                            }
                            else state.select(touchedNode)
                        }
                        if (dragStart != null) {
                            onDrag(dragStart.position)
                            drag(dragStart.id) { change ->
                                onDrag(change.position)
                                change.consume()
                            }
                        }
                        if (isLinkDrag) state.onLinkDrop(currentOnNodeLink) else state.onTouchUp(currentOnNodeMove)
                        link?.let { (node, url) -> currentOnLinkClick(node, url) }
                        // A second click on the same node shortly after the first is a double click; a drag is not a
                        // click, nor is one that changes which nodes are selected.
                        val isSecondClick = !isCtrl && lastClickedNodeId == touchedNode.id &&
                            down.uptimeMillis - lastClickTime <= viewConfiguration.doubleTapTimeoutMillis
                        if (dragStart != null || isSecondClick || isCtrl) {
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
                            PointerEventType.Press -> state.onHover(null)
                            PointerEventType.Exit -> state.onPointerExit()
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
            drawMap(state, drawInfo, measure, lineColor) {
                if (onAddChildClick != null) drawAddButtons(state, drawInfo, lineColor, addIconColor)
            }
            drawLinkDrag(state)

            state.selectedNodes.filter { state.tree.isVisible(it.id) }.forEach { selectedNode ->
                NodeDrawerFactory(
                    selectedNode
                    , drawInfo
                    , this
                )
                .createStrokeNode()
                .drawNode(this)
                // The note marker sits on the node's edge, so it is drawn again to stay above the border.
                if (selectedNode.note.isNotBlank()) drawNoteMarker(noteMarkerCenter(selectedNode), drawInfo)
            }
        }
    }
}

/**
 * Draws the mind map itself, in the pixels of the tree: its lines, its fold markers, its nodes and its arrow
 * links. What is drawn over the lines and under the nodes, as the buttons that add a child, is [underNodes].
 */
internal fun DrawScope.drawMap(
    state: MindMapState
    , drawInfo: DrawInfo
    , measureTextSize: MeasureTextSize
    , lineColor: Color
    , underNodes: DrawScope.() -> Unit = {}
) {
    drawLines(state, drawInfo, lineColor)
    underNodes()
    drawFoldMarkers(state, drawInfo, lineColor)
    drawTree(state, drawInfo, measureTextSize)
    drawArrowLinks(state)
}

private fun DrawScope.drawTree(
    state: MindMapState,
    drawInfo: DrawInfo,
    measureTextSize: MeasureTextSize,
) {
    state.tree.doVisiblePreorderTraversal { node ->
        val nodeDrawer = NodeDrawerFactory(node, drawInfo, this).createNodeDrawer()
        nodeDrawer.drawNode(this)
        val content = measureTextSize.measureContent(state.tree, node, state.showAttributes)
        // The drawer centers the text on the node, which the attributes below it share with the text.
        translate(top = -content.textRise) { nodeDrawer.drawText(this, content.text) }
        content.table?.let { drawAttributeTable(node, it, content.tableTop) }
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

// A grid in the color of the node's text, with the names in its left column and the values in its right one.
private fun DrawScope.drawAttributeTable(
    node: NodeData<*>
    , table: AttributeTable
    , tableTop: Float
) {
    val left = node.path.centerX.toPx(this) - table.width / 2
    val top = node.path.centerY.toPx(this) + tableTop
    val lineColor = node.textColor.copy(alpha = ATTRIBUTE_LINE_ALPHA)
    val lineWidth = ATTRIBUTE_LINE_WIDTH.toPx(this)

    drawRect(lineColor, Offset(left, top), Size(table.width, table.height), style = Stroke(lineWidth))
    drawLine(
        lineColor, Offset(left + table.nameWidth, top), Offset(left + table.nameWidth, top + table.height), lineWidth,
    )
    var rowTop = top
    table.rows.forEachIndexed { index, row ->
        if (index > 0) drawLine(lineColor, Offset(left, rowTop), Offset(left + table.width, rowTop), lineWidth)
        drawText(row.name, node.textColor, Offset(left + table.paddingX, rowTop + table.paddingY))
        drawText(row.value, node.textColor, Offset(left + table.nameWidth + table.paddingX, rowTop + table.paddingY))
        rowTop += row.height
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
        is SquareNodeData -> {
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
            is SquareNodeData -> attachedNode.path.height
            is StadiumNodeData -> attachedNode.path.height()
            is OvalNodeData -> attachedNode.path.height()
            is CircleNodeData -> attachedNode.path.radius + Dp(ATTACH_CIRCLE_NODE_RANGE_VALUE)
        }
        val width = when (attachedNode) {
            is RectangleNodeData -> attachedNode.path.width
            is SquareNodeData -> attachedNode.path.width
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
            val side = tree.sideOf(toNode.id)
            val isRightSide = !side.isLeft
            // The lines to the children on a slanted side of the root leave it along that side's line.
            val start =
                if (node.parentId == null && side.isSlanted) rootEdgePoint(node, side, Dp(0f))
                else Offset(getNodeEdgeX(node, isRightEdge = isRightSide), node.path.centerY.toPx(this))
            val startX = start.x
            val startY = start.y

            val endX = getNodeEdgeX(toNode, isRightEdge = isRightSide.not())
            val endY = toNode.path.centerY.toPx(this)

            path.reset()
            path.moveTo(startX, startY)
            if (node.parentId == null && side.isSlanted) {
                // The line turns up or down right by the root, before the children straight out to that side,
                // and so runs past them rather than across them.
                val bendX = startX + (if (isRightSide) 1f else -1f) * SLANTED_LINE_BEND.toPx(this)
                path.cubicTo(bendX, endY, bendX, endY, endX, endY)
            }
            else {
                val midX = (startX + endX) / 2
                path.cubicTo(midX, startY, midX, endY, endX, endY)
            }

            drawPath(
                path = path
                , color = color
                , style = stroke
            )
        }
    }
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
        is SquareNodeData -> {
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

// The arrow link being dragged, from the node it starts at to the pointer.
private fun DrawScope.drawLinkDrag(state: MindMapState) {
    val dragPoint = state.linkDragPoint ?: return
    val from = state.selectedNode ?: return
    val dash = LINK_DRAG_DASH.toPx(this)
    drawLine(
        color = ArrowLink.DEFAULT_COLOR
        , start = edgePoint(from, dragPoint)
        , end = dragPoint
        , strokeWidth = ARROW_LINK_WIDTH.toPx(this)
        , pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
    )
}

/**
 * Calls [action] for every node that is shown with the color of the lines of its branch: each child of the root starts
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
        tree.visibleChildren(node).forEachIndexed { index, childId ->
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

    val shownIds = state.addButtonNodes().map { it.id }.toSet()
    forEachNodeWithLineColor(state.tree, drawInfo, lineColor) { node, color ->
        if (node.id !in shownIds) return@forEachNodeWithLineColor
        state.addButtonSides(node).forEach { side ->
            val center = addButtonCenter(node, side)
            drawCircle(color = color, radius = radius, center = center)
            drawLine(iconColor, center - Offset(arm, 0f), center + Offset(arm, 0f), strokeWidth)
            drawLine(iconColor, center - Offset(0f, arm), center + Offset(0f, arm), strokeWidth)
        }
    }
}

// As FreeMind marks a folded node: a small circle where the lines to its hidden children would start.
private fun DrawScope.drawFoldMarkers(
    state: MindMapState
    , drawInfo: DrawInfo
    , lineColor: Color
) {
    val strokeWidth = FOLD_MARKER_WIDTH.toPx(this)
    val radius = ADD_BUTTON_RADIUS.toPx(this) - strokeWidth / 2

    forEachNodeWithLineColor(state.tree, drawInfo, lineColor) { node, color ->
        if (state.tree.isFolded(node.id)) {
            drawCircle(color = color, radius = radius, center = foldMarkerCenter(state.tree, node), style = Stroke(strokeWidth))
        }
    }
}

// The marker is where the add button of the node is while it is not folded.
internal fun Density.foldMarkerCenter(
    tree: Tree<*>
    , node: NodeData<*>
): Offset = addButtonCenter(node, tree.sideOf(node.id))

// The button touches the node's edge where its lines start, so it covers the start of the lines, not the node.
internal fun Density.addButtonCenter(
    node: NodeData<*>
    , side: LayoutSide = LayoutSide.RIGHT
): Offset {
    // The root's buttons are each on the line of their side, so they are evenly spaced around it.
    if (node.parentId == null) return rootEdgePoint(node, side, ADD_BUTTON_RADIUS)
    val radius = ADD_BUTTON_RADIUS.toPx(this)
    val centerX =
        if (side.isLeft) getNodeEdgeX(node, isRightEdge = false) - radius
        else getNodeEdgeX(node, isRightEdge = true) + radius
    return Offset(centerX, node.path.centerY.toPx(this))
}

// The point [beyond] out of the edge of the root, which is an oval, where the button and the lines of [side]
// are. The six sides are spaced evenly along the edge, starting from its right end, and the point is out of
// the edge square to it.
internal fun Density.rootEdgePoint(
    root: NodeData<*>
    , side: LayoutSide
    , beyond: Dp
): Offset {
    val (radiusX, radiusY) = when (root) {
        is OvalNodeData -> root.path.radiusX.toPx(this) to root.path.radiusY.toPx(this)
        else -> (getNodeEdgeX(root, isRightEdge = true) - root.path.centerX.toPx(this)).let { it to it }
    }
    val sideIndex = ((side.angleDegrees / 60f).roundToInt() + LayoutSide.entries.size) % LayoutSide.entries.size
    val t = parameterAtArcFraction(radiusX, radiusY, sideIndex.toFloat() / LayoutSide.entries.size)
    // Up is the negative y of the screen.
    val edge = Offset(radiusX * cos(t), -radiusY * sin(t))
    val normal = Offset(radiusY * cos(t), -radiusX * sin(t)).let { it / it.getDistance() }
    return Offset(root.path.centerX.toPx(this), root.path.centerY.toPx(this)) + edge + normal * beyond.toPx(this)
}

// The angle t of the point (radiusX cos t, radiusY sin t) of an ellipse that is [fraction] of the way round its
// edge, counterclockwise from its right end.
private fun parameterAtArcFraction(
    radiusX: Float
    , radiusY: Float
    , fraction: Float
): Float {
    val steps = ROOT_EDGE_STEPS
    val step = 2f * PI.toFloat() / steps
    val lengths = FloatArray(steps + 1)
    for (i in 1..steps) {
        val t = (i - 0.5f) * step
        lengths[i] = lengths[i - 1] + step * sqrt((radiusX * sin(t)).pow(2) + (radiusY * cos(t)).pow(2))
    }
    val target = lengths[steps] * fraction
    val i = (1..steps).firstOrNull { lengths[it] >= target } ?: return 0f
    val segment = lengths[i] - lengths[i - 1]
    val within = if (segment > 0f) (target - lengths[i - 1]) / segment else 0f
    return (i - 1 + within) * step
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
            is SquareNodeData -> node.path.width.toPx(this) / 2
            is StadiumNodeData -> node.path.width().toPx(this) / 2
            is OvalNodeData -> node.path.width().toPx(this) / 2
        }
    return if (isRightEdge) nodeCenterX + widthOffset else nodeCenterX - widthOffset
}
