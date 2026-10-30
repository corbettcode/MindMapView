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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import com.mindsync.library.data.ArrowLinkLineStyle
import com.mindsync.library.data.ArrowLinkThickness
import com.mindsync.library.data.CircleNodeData
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.NodeLineStyle
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.LineNodeData
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
private val ARROW_LINK_DASH = Dp(8f)
private val ARROW_LINK_GAP = Dp(6f)
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
 * The attributes of a node are drawn as a table below its text while [MindMapState.showAttributes] is set, and
 * its note as a paragraph between the two while [MindMapState.showNotes] is set, in place of its note marker.
 *
 * Dragging a node onto another while Ctrl is held down adds an arrow link from the first to the second
 * instead of moving it, or removes that arrow link when there is one already. [onNodeLink] is then called
 * with whether the nodes are linked now. See [MindMapState.addArrowLink].
 *
 * Dragging a node and dropping it on no other node moves it up or down among its siblings on the same side
 * of their parent, to where it was dropped.
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
    , emojiFontFamily: FontFamily? = null
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
    val measureTextSize = remember(textMeasurer, density, fontFamily, emojiFontFamily) {
        val textStyle = TextStyle(
            fontSize = with(density) { drawInfo.textSize.dpVal.dp.toSp() },
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        )
        MeasureTextSize(textMeasurer, textStyle, density, drawInfo, emojiFontFamily)
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

    var isArrowLinkPressed by remember { mutableStateOf(false) }
    var isNodeDragged by remember { mutableStateOf(false) }
    var isMapDragged by remember { mutableStateOf(false) }

    DisposableEffect(state, measureTextSize, density, scope) {
        state.attach(measureTextSize, density, scope)
        onDispose { state.detach() }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { state.onSizeChanged(it.toSize()) }
            // An arrow link is dragged to bend it and right clicked for its menu, so the pointer shows it can be,
            // and keeps showing it while the link is held. A node and the map show it while they are dragged.
            .pointerHoverIcon(
                if (state.isArrowLinkHovered || isArrowLinkPressed || isNodeDragged || isMapDragged) PointerIcon.Hand
                else PointerIcon.Default
            )
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
                        // Away from the nodes, a drag that starts on an arrow link reshapes it instead of panning.
                        val draggedLink = state.findArrowLink(down.position)
                        if (draggedLink != null) {
                            down.consume()
                            isArrowLinkPressed = true
                            try {
                                val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                if (dragStart != null) {
                                    state.onArrowLinkDragStart(draggedLink, down.position)
                                    state.onArrowLinkDrag(dragStart.position)
                                    drag(dragStart.id) { change ->
                                        state.onArrowLinkDrag(change.position)
                                        change.consume()
                                    }
                                    state.onArrowLinkDrop()
                                }
                            } finally {
                                isArrowLinkPressed = false
                            }
                        }
                        else {
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    state.onTransform(event.calculatePan(), event.calculateZoom())
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            isMapDragged = true
                                            change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                isMapDragged = false
                            }
                        }
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
                            isNodeDragged = true
                            try {
                                onDrag(dragStart.position)
                                drag(dragStart.id) { change ->
                                    onDrag(change.position)
                                    change.consume()
                                }
                            } finally {
                                isNodeDragged = false
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
                if (selectedNode.note.isNotBlank() && !state.showNotes) {
                    drawNoteMarker(noteMarkerCenter(selectedNode), drawInfo)
                }
            }
        }
    }
}

/**
 * Draws the mind map itself, in the pixels of the tree: its lines, its arrow links, its fold markers and its nodes.
 * The arrow links are behind the nodes, as a click where both are goes to the node. What is drawn over the lines
 * and under the nodes, as the buttons that add a child, is [underNodes].
 */
internal fun DrawScope.drawMap(
    state: MindMapState
    , drawInfo: DrawInfo
    , measureTextSize: MeasureTextSize
    , lineColor: Color
    , underNodes: DrawScope.() -> Unit = {}
) {
    drawLines(state, drawInfo, lineColor)
    drawArrowLinks(state)
    underNodes()
    drawFoldMarkers(state, drawInfo, lineColor)
    drawTree(state, drawInfo, measureTextSize, lineColor)
}

private fun DrawScope.drawTree(
    state: MindMapState,
    drawInfo: DrawInfo,
    measureTextSize: MeasureTextSize,
    lineColor: Color,
) {
    // A line node is drawn as its own line goes on from the one to its parent, in the same color and width.
    val lines = mutableMapOf<String, ResolvedLine>()
    forEachNodeWithLine(state.tree, drawInfo, lineColor) { node, line ->
        if (node is LineNodeData) lines[node.id] = line
    }
    state.tree.doVisiblePreorderTraversal { node ->
        val line = lines[node.id]
        val nodeDrawer = NodeDrawerFactory(node, drawInfo, this, line?.color, line?.width).createNodeDrawer()
        nodeDrawer.drawNode(this)
        val content = measureTextSize.measureContent(state.tree, node, state.showAttributes, state.showNotes)
        // The drawer centers the text on the node, which the note and attributes below it share with the text.
        translate(top = -content.textRise) { nodeDrawer.drawText(this, content.text) }
        content.note?.let { note ->
            val centerX = node.path.centerX.toPx(this)
            val centerY = node.path.centerY.toPx(this)
            // The line spans the text and the note, so it is as wide as the wider of the two.
            val lineHalfWidth = maxOf(content.text.size.width, note.size.width) / 2f
            drawLine(
                node.textColor.copy(alpha = ATTRIBUTE_LINE_ALPHA)
                , Offset(centerX - lineHalfWidth, centerY + content.noteLineY)
                , Offset(centerX + lineHalfWidth, centerY + content.noteLineY)
                , ATTRIBUTE_LINE_WIDTH.toPx(this)
            )
            drawText(note, node.textColor, Offset(centerX - note.size.width / 2f, centerY + content.noteTop))
        }
        content.table?.let { drawAttributeTable(node, it, content.tableTop) }
        // A note shown in the node needs no marker to tell it is there.
        if (node.note.isNotBlank() && !state.showNotes) drawNoteMarker(noteMarkerCenter(node), drawInfo)
    }
}

// A line node has no fill to hide the arrow links behind it, so they are kept out of its box instead.
private fun DrawScope.drawArrowLinks(
    state: MindMapState
) {
    val lineNodeBoxes = Path()
    state.tree.doVisiblePreorderTraversal { node ->
        if (node is LineNodeData) {
            lineNodeBoxes.addRect(
                Rect(
                    node.path.leftX().toPx(this)
                    , node.path.topY().toPx(this)
                    , node.path.rightX().toPx(this)
                    , node.path.bottomY().toPx(this)
                )
            )
        }
    }
    if (lineNodeBoxes.isEmpty) drawArrowLinkCurves(state)
    else clipPath(lineNodeBoxes, ClipOp.Difference) { drawArrowLinkCurves(state) }
}

private fun DrawScope.drawArrowLinkCurves(
    state: MindMapState
) {
    val tree = state.tree
    val path = Path()

    tree.arrowLinks.forEach { link ->
        val curve = arrowLinkCurve(tree, link) ?: return@forEach
        path.reset()
        path.moveTo(curve.start.x, curve.start.y)
        path.cubicTo(
            curve.startControl.x, curve.startControl.y, curve.endControl.x, curve.endControl.y, curve.end.x, curve.end.y,
        )
        drawPath(path, link.color, style = arrowLinkStroke(link.lineStyle, link.thickness))
        // A head grows with the line, but by less, so a thin line still has a head that is easy to see.
        val headScale = (link.thickness.width.dpVal + ARROW_LINK_WIDTH.dpVal) / (2 * ARROW_LINK_WIDTH.dpVal)
        if (link.hasStartArrow) drawArrowHead(curve.start, curve.startControl, curve.end, link.color, headScale)
        if (link.hasEndArrow) drawArrowHead(curve.end, curve.endControl, curve.start, link.color, headScale)
    }
}

// A dot is a dash of almost no length, which the round cap draws as a dot as wide as the line. The round caps
// also eat into the gaps, so a thick line spreads its pattern out to keep them open.
private fun DrawScope.arrowLinkStroke(
    lineStyle: ArrowLinkLineStyle
    , thickness: ArrowLinkThickness
): Stroke {
    val spread = maxOf(1f, thickness.width.dpVal / ARROW_LINK_WIDTH.dpVal)
    val dash = ARROW_LINK_DASH.toPx(this) * spread
    val gap = ARROW_LINK_GAP.toPx(this) * spread
    val dot = 0.01f
    val pathEffect = when (lineStyle) {
        ArrowLinkLineStyle.SOLID -> null
        ArrowLinkLineStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(dash, gap))
        ArrowLinkLineStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(dot, gap))
        ArrowLinkLineStyle.DASH_DOT -> PathEffect.dashPathEffect(floatArrayOf(dash, gap, dot, gap))
    }
    return Stroke(
        width = thickness.width.toPx(this)
        , cap = StrokeCap.Round
        , pathEffect = pathEffect
    )
}

// The head points the way the curve arrives at its tip: away from the control point of that end, or from
// the other end when the control point is on the tip.
private fun DrawScope.drawArrowHead(
    tip: Offset
    , control: Offset
    , otherEnd: Offset
    , color: Color
    , scale: Float
) {
    val along = (tip - control).takeIf { it.getDistance() > 0f } ?: (tip - otherEnd)
    val length = along.getDistance()
    if (length == 0f) return
    val direction = along / length
    val across = Offset(-direction.y, direction.x) * ARROW_HEAD_HALF_WIDTH.toPx(this) * scale
    val base = tip - direction * ARROW_HEAD_LENGTH.toPx(this) * scale
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
        is LineNodeData -> {
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
            is LineNodeData -> attachedNode.path.height
            is StadiumNodeData -> attachedNode.path.height()
            is OvalNodeData -> attachedNode.path.height()
            is CircleNodeData -> attachedNode.path.radius + Dp(ATTACH_CIRCLE_NODE_RANGE_VALUE)
        }
        val width = when (attachedNode) {
            is RectangleNodeData -> attachedNode.path.width
            is SquareNodeData -> attachedNode.path.width
            is LineNodeData -> attachedNode.path.width
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

    forEachNodeWithLine(tree, drawInfo, lineColor) { toNode, line ->
        val node = toNode.parentId?.let { tree.getNode(it) } ?: return@forEachNodeWithLine
        if (manager.getMovingState().not() || manager.getSelectedNode()?.id != toNode.id) {
            val side = tree.sideOf(toNode.id)
            val isRightSide = !side.isLeft
            val isSlanted = node.parentId == null && side.isSlanted
            // The lines to the children on a slanted side of the root leave it along that side's line.
            val start =
                if (isSlanted) rootEdgePoint(node, side, Dp(0f))
                else Offset(getNodeEdgeX(node, isRightEdge = isRightSide), lineEndY(node))
            val end = Offset(getNodeEdgeX(toNode, isRightEdge = isRightSide.not()), lineEndY(toNode))
            val curve =
                if (isSlanted) {
                    // The line turns up or down right by the root, before the children straight out to that side,
                    // and so runs past them rather than across them.
                    val bend = Offset(start.x + (if (isRightSide) 1f else -1f) * SLANTED_LINE_BEND.toPx(this), end.y)
                    LineCurve(start, bend, bend, end)
                }
                else {
                    val midX = (start.x + end.x) / 2
                    LineCurve(start, Offset(midX, start.y), Offset(midX, end.y), end)
                }
            val width = line.width.toPx(this)
            path.reset()
            when (line.style) {
                NodeLineStyle.LINEAR -> path.addPolyline(curve.corners(isSlanted))
                NodeLineStyle.BEZIER -> path.addCurve(curve)
                NodeLineStyle.SHARP_LINEAR -> path.addWedge(curve.corners(isSlanted), sharpBase(width))
                NodeLineStyle.SHARP_BEZIER -> path.addCurvedWedge(curve, sharpBase(width))
            }
            val isSharp = line.style == NodeLineStyle.SHARP_LINEAR || line.style == NodeLineStyle.SHARP_BEZIER
            drawPath(
                path = path
                , color = line.color
                , style = if (isSharp) Fill else Stroke(width = width)
            )
        }
    }
}

// The start, the two control points and the end of the bezier curve of a line from a parent to its child.
private class LineCurve(
    val start: Offset
    , val startControl: Offset
    , val endControl: Offset
    , val end: Offset
) {
    // A straight line goes through the bend of a line to a slanted side of the root, as the curve does.
    fun corners(isBent: Boolean): List<Offset> =
        if (isBent) listOf(start, startControl, end) else listOf(start, end)
}

// A sharp line thinner than this would barely show at its start, let alone further along.
private val SHARP_LINE_MIN_BASE = Dp(1.5f)

private fun Density.sharpBase(width: Float): Float = maxOf(width, SHARP_LINE_MIN_BASE.toPx(this))

private fun Path.addPolyline(points: List<Offset>) {
    moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun Path.addCurve(curve: LineCurve) {
    moveTo(curve.start.x, curve.start.y)
    cubicTo(
        curve.startControl.x, curve.startControl.y, curve.endControl.x, curve.endControl.y, curve.end.x, curve.end.y
    )
}

// The unit vector at a right angle to the line from [from] to [to], or straight down when they are one point.
private fun normalOf(
    from: Offset
    , to: Offset
): Offset {
    val direction = to - from
    val length = direction.getDistance()
    return if (length == 0f) Offset(0f, 1f) else Offset(-direction.y / length, direction.x / length)
}

/**
 * Adds a closed shape along the straight lines through [points]: [base] wide at the first point, at a right angle
 * to the first line, and narrowing evenly along the lines to nothing at the last point.
 */
private fun Path.addWedge(
    points: List<Offset>
    , base: Float
) {
    val lengths = points.zipWithNext { a, b -> (b - a).getDistance() }
    val total = lengths.sum()
    var along = 0f
    val offsets = points.mapIndexed { index, point ->
        if (index > 0) along += lengths[index - 1]
        val halfWidth = if (total == 0f) 0f else base / 2 * (1 - along / total)
        val normal = when (index) {
            0 -> normalOf(points[0], points[1])
            points.lastIndex -> normalOf(points[index - 1], point)
            else -> {
                // Where two lines meet, the sides meet at the corner between them, as wide from each line.
                val before = normalOf(points[index - 1], point)
                val after = normalOf(point, points[index + 1])
                val sum = before + after
                val length = sum.getDistance()
                if (length == 0f) before else sum / length / maxOf((sum / length).dot(after), MIN_MITER)
            }
        }
        normal * halfWidth
    }
    val first = points.first() + offsets.first()
    moveTo(first.x, first.y)
    points.indices.drop(1).forEach { index ->
        val point = points[index] + offsets[index]
        lineTo(point.x, point.y)
    }
    points.indices.reversed().forEach { index ->
        val point = points[index] - offsets[index]
        lineTo(point.x, point.y)
    }
    close()
}

// How sharp a corner of a sharp line can be before its sides stop growing further out at it.
private const val MIN_MITER = 0.25f

private fun Offset.dot(other: Offset): Float = x * other.x + y * other.y

/**
 * Adds a closed shape along [curve]: [base] wide at its start, at a right angle to the curve there, with sides that
 * are curves themselves and meet at its end.
 */
private fun Path.addCurvedWedge(
    curve: LineCurve
    , base: Float
) {
    // The start and its control point are moved out alike, so each side leaves the start as the curve does.
    val offset = normalOf(curve.start, curve.startControl) * (base / 2)
    val first = curve.start + offset
    val firstControl = curve.startControl + offset
    val last = curve.start - offset
    val lastControl = curve.startControl - offset
    moveTo(first.x, first.y)
    cubicTo(
        firstControl.x, firstControl.y, curve.endControl.x, curve.endControl.y, curve.end.x, curve.end.y
    )
    cubicTo(curve.endControl.x, curve.endControl.y, lastControl.x, lastControl.y, last.x, last.y)
    close()
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
        is LineNodeData -> {
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
    forEachNodeWithLine(state.tree, drawInfo, lineColor) { node, line ->
        if (node.id !in shownIds) return@forEachNodeWithLine
        state.addButtonSides(node).forEach { side ->
            val center = addButtonCenter(node, side)
            drawCircle(color = line.color, radius = radius, center = center)
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

    forEachNodeWithLine(state.tree, drawInfo, lineColor) { node, line ->
        if (state.tree.isFolded(node.id)) {
            drawCircle(
                color = line.color
                , radius = radius
                , center = foldMarkerCenter(state.tree, node)
                , style = Stroke(strokeWidth)
            )
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
    return Offset(centerX, lineEndY(node))
}

// The lines of a node meet it halfway down its sides, but those of a line node meet the ends of its line.
private fun Density.lineEndY(
    node: NodeData<*>
): Float = when (node) {
    is LineNodeData -> node.path.bottomY().toPx(this)
    else -> node.path.centerY.toPx(this)
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
            is LineNodeData -> node.path.width.toPx(this) / 2
            is StadiumNodeData -> node.path.width().toPx(this) / 2
            is OvalNodeData -> node.path.width().toPx(this) / 2
        }
    return if (isRightEdge) nodeCenterX + widthOffset else nodeCenterX - widthOffset
}
