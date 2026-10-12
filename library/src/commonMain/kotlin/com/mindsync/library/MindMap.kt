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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
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

internal val ADD_BUTTON_RADIUS = Dp(10f)

/**
 * Draws the mind map held by [state]. The mind map fills the size given by [modifier], so pass a size
 * modifier such as `Modifier.fillMaxSize()`.
 *
 * When [onAddChildClick] is given, every node gets a '+' button in the color of the lines, drawn where
 * its lines to its children start. Clicking it selects that node and calls [onAddChildClick] with it, so
 * [MindMapState.addNode] then adds the child to it.
 */
@Composable
fun MindMap(
    state: MindMapState
    , modifier: Modifier = Modifier
    , fontFamily: FontFamily? = null
    , onNodeClick: (node: NodeData<*>?) -> Unit = {}
    , onNodeDoubleClick: (node: NodeData<*>) -> Unit = {}
    , onNodeMove: (tree: Tree<*>, target: NodeData<*>, parent: NodeData<*>) -> Unit = { _, _, _ -> }
    , onAddChildClick: ((node: NodeData<*>) -> Unit)? = null
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
                    val addButtonNode =
                        if (currentOnAddChildClick != null) state.findAddButtonNode(down.position) else null
                    if (addButtonNode != null) {
                        down.consume()
                        lastClickedNodeId = null
                        waitForUpOrCancellation()?.let { up ->
                            up.consume()
                            state.onAddButtonClick(addButtonNode)
                            currentOnAddChildClick?.invoke(addButtonNode)
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
                drawAddButtons(state, lineColor, addIconColor)
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
    }
}

private fun DrawScope.drawAttachedNode(
    state: MindMapState
    , drawInfo: DrawInfo
) {
    state.attachedNode?.let { attachedNode ->
        val height = when (attachedNode) {
            is RectangleNodeData -> attachedNode.path.height
            is StadiumNodeData -> attachedNode.path.height()
            is CircleNodeData -> attachedNode.path.radius + Dp(ATTACH_CIRCLE_NODE_RANGE_VALUE)
        }
        val width = when (attachedNode) {
            is RectangleNodeData -> attachedNode.path.width
            is StadiumNodeData -> attachedNode.path.width()
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
    val isRightLayout = state.layoutSide == LayoutSide.RIGHT

    fun traverseLine(node: NodeData<*>) {
        for (toNodeId in node.children) {
            val toNode = tree.getNode(toNodeId)
            if (manager.getMovingState().not() || manager.getSelectedNode()?.id != toNode.id) {
                val startX = getNodeEdgeX(node, isRightEdge = isRightLayout)
                val startY = node.path.centerY.toPx(this)

                val endX = getNodeEdgeX(toNode, isRightEdge = isRightLayout.not())
                val endY = toNode.path.centerY.toPx(this)

                val midX = (startX + endX) / 2

                path.reset()
                path.moveTo(startX, startY)
                path.cubicTo(midX, startY, midX, endY, endX, endY)

                drawPath(
                    path = path
                    , color = lineColor
                    , style = stroke
                )
            }
            traverseLine(toNode)
        }
    }
    traverseLine(tree.getRootNode())
}

private fun DrawScope.drawAddButtons(
    state: MindMapState
    , lineColor: Color
    , iconColor: Color
) {
    val radius = ADD_BUTTON_RADIUS.toPx(this)
    val arm = radius * ADD_BUTTON_ARM_RATIO
    val strokeWidth = radius * ADD_BUTTON_STROKE_RATIO

    state.tree.doPreorderTraversal { node ->
        val center = addButtonCenter(node, state.layoutSide)
        drawCircle(color = lineColor, radius = radius, center = center)
        drawLine(iconColor, center - Offset(arm, 0f), center + Offset(arm, 0f), strokeWidth)
        drawLine(iconColor, center - Offset(0f, arm), center + Offset(0f, arm), strokeWidth)
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
        }
    return if (isRightEdge) nodeCenterX + widthOffset else nodeCenterX - widthOffset
}
