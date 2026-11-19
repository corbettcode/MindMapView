package com.mindsync.library.outline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsync.library.MindMapState
import com.mindsync.library.TASK_BADGE_STROKE
import com.mindsync.library.TextEditHost
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.TaskDate
import com.mindsync.library.drawTaskBadge
import com.mindsync.library.layout.OVERDUE_COLOR
import com.mindsync.library.layout.TASK_BADGE_SIZE
import com.mindsync.library.layout.TaskBadge
import com.mindsync.library.layout.taskBadges
import com.mindsync.library.util.RichText
import com.mindsync.library.util.withEmojiFont

/** A press on a row: where it was in the row's [coordinates], with which keys and button, and the link under it. */
internal class RowPress(
    val position: Offset
    , val coordinates: LayoutCoordinates
    , val isSecondary: Boolean
    , val isShift: Boolean
    , val isCtrl: Boolean
    , val link: String?
    , val uptimeMillis: Long
    , val doubleClickMillis: Long
)

// The row of one topic: the arrow that folds it, its bullet, badges, text, markers and due date.
@Composable
internal fun OutlineRowView(
    state: MindMapState
    , row: OutlineRow
    , isSelected: Boolean
    , isFaded: Boolean
    , textColor: Color
    , topicFontFamily: FontFamily?
    , emojiFontFamily: FontFamily?
    , dateLabel: (date: TaskDate) -> String
    , onAddSiblingToType: (isAbove: Boolean) -> Unit
    , onPress: (node: NodeData<*>, press: RowPress) -> Unit
    , onFoldClick: (node: NodeData<*>) -> Unit
) {
    val tree = state.tree
    val node = tree.nodes[row.id] ?: return
    val currentOnPress by rememberUpdatedState(onPress)
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = remember(node.description, emojiFontFamily) { rowText(node.description, emojiFontFamily) }
    val task = tree.taskOf(row.id)
    val attributeCount = tree.attributesOf(row.id).size
    val selectionColor = SELECTION_COLOR.copy(alpha = if (state.isDark) 0.35f else 0.18f)

    Row(
        verticalAlignment = Alignment.CenterVertically
        , modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .background(if (isSelected) selectionColor else Color.Transparent)
            .alpha(if (isFaded) FADED_ALPHA else 1f)
            .onGloballyPositioned { rowCoordinates = it }
            .pointerInput(row.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        val change = event.changes.firstOrNull() ?: continue
                        // The arrow that folds the topic takes its own presses.
                        if (change.isConsumed) continue
                        val coordinates = rowCoordinates ?: continue
                        val current = tree.nodes[row.id] ?: continue
                        change.consume()
                        val link = textLayout?.let { layout ->
                            val inText = textCoordinates?.localPositionOf(coordinates, change.position)
                                ?: return@let null
                            val isOnText = inText.x in 0f..layout.size.width.toFloat() &&
                                inText.y in 0f..layout.size.height.toFloat()
                            if (!isOnText) return@let null
                            val offset = layout.getOffsetForPosition(inText)
                            layout.layoutInput.text
                                .getStringAnnotations(RichText.LINK_TAG, offset, offset)
                                .firstOrNull()?.item
                        }
                        val modifiers = event.keyboardModifiers
                        currentOnPress(
                            current
                            , RowPress(
                                position = change.position
                                , coordinates = coordinates
                                , isSecondary = event.buttons.isSecondaryPressed
                                , isShift = modifiers.isShiftPressed
                                , isCtrl = modifiers.isCtrlPressed || modifiers.isMetaPressed
                                , link = link
                                , uptimeMillis = change.uptimeMillis
                                , doubleClickMillis = viewConfiguration.doubleTapTimeoutMillis
                            )
                        )
                    }
                }
            }
            .padding(start = ROW_START + INDENT * row.depth, end = 12.dp)
            .testTag(outlineRowTag(row.id))
    ) {
        FoldArrow(row, textColor) { onFoldClick(node) }
        Bullet(tree.fillOf(row.id), textColor)
        Spacer(Modifier.width(8.dp))
        TaskBadges(state, row.id, textColor)
        val textStyle = TextStyle(
            color = textColor
            , fontSize = 14.sp
            , fontFamily = topicFontFamily
            , fontWeight = if (row.depth == 0) FontWeight.Bold else null
            , fontStyle = if (row.isSummary) FontStyle.Italic else null
        )
        val isTyping = state.textEditNodeId == row.id && state.textEditHost == TextEditHost.OUTLINE
        if (isTyping) {
            OutlineTextEditor(
                state = state
                , id = row.id
                , html = node.description
                , textStyle = textStyle
                , onNextTopic = { onAddSiblingToType(false) }
                , modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 4.dp)
            )
        }
        else {
            BasicText(
                text = text
                , style = textStyle
                , onTextLayout = { textLayout = it }
                , modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 4.dp)
                    .onGloballyPositioned { textCoordinates = it }
            )
        }
        if (node.note.isNotBlank()) NoteMark(textColor)
        if (attributeCount > 0) AttributeMark(attributeCount, textColor)
        task.due?.let { due ->
            BasicText(
                text = dateLabel(due)
                , style = TextStyle(
                    color = if (state.isOverdue(row.id)) OVERDUE_COLOR else textColor.copy(alpha = 0.6f)
                    , fontSize = 12.sp
                )
                , modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// The text of a topic as its row shows it: a summary with a brace before it, and a sign for each picture, which
// is too big for a row.
private fun rowText(
    html: String
    , emojiFontFamily: FontFamily?
): AnnotatedString {
    val images = RichText.imageSources(html).size
    val text = RichText.parse(RichText.withoutImages(html))
    return buildAnnotatedString {
        append(text)
        repeat(images) { append(" 🖼") }
    }.withEmojiFont(emojiFontFamily)
}

@Composable
private fun FoldArrow(
    row: OutlineRow
    , color: Color
    , onClick: () -> Unit
) {
    val canFold = row.hasChildren && row.depth > 0
    Canvas(
        modifier = Modifier
            .size(FOLD_ARROW_SIZE)
            .testTag(outlineFoldTag(row.id))
            .then(
                if (canFold) {
                    Modifier
                        .pointerInput(row.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Press) continue
                                    event.changes.forEach { it.consume() }
                                    onClick()
                                }
                            }
                        }
                }
                else {
                    Modifier
                }
            )
    ) {
        if (!canFold) return@Canvas
        val half = size.minDimension * 0.22f
        val center = Offset(size.width / 2, size.height / 2)
        val path = Path().apply {
            if (row.isFolded) {
                moveTo(center.x - half * 0.6f, center.y - half)
                lineTo(center.x + half * 0.9f, center.y)
                lineTo(center.x - half * 0.6f, center.y + half)
            }
            else {
                moveTo(center.x - half, center.y - half * 0.6f)
                lineTo(center.x + half, center.y - half * 0.6f)
                lineTo(center.x, center.y + half * 0.9f)
            }
            close()
        }
        drawPath(path, color.copy(alpha = 0.7f))
    }
}

@Composable
private fun Bullet(
    fill: Color
    , outline: Color
) {
    Canvas(modifier = Modifier.size(BULLET_SIZE)) {
        val radius = size.minDimension / 2
        drawCircle(fill, radius)
        drawCircle(outline.copy(alpha = 0.35f), radius - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun TaskBadges(
    state: MindMapState
    , id: String
    , color: Color
) {
    val badges = taskBadges(state.tree.taskOf(id))
    if (badges.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    badges.forEach { badge ->
        Canvas(modifier = Modifier.padding(end = 4.dp).size(TASK_BADGE_SIZE)) {
            val number = (badge as? TaskBadge.Priority)?.let {
                textMeasurer.measure(
                    it.priority.toString()
                    , TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
            drawTaskBadge(
                badge
                , Offset(size.width / 2, size.height / 2)
                , size.minDimension / 2
                , TASK_BADGE_STROKE.toPx()
                , color
                , state.background
                , number
            )
        }
    }
    Spacer(Modifier.width(2.dp))
}

// A page with lines on it, as the map's note marker.
@Composable
private fun NoteMark(color: Color) {
    Box(modifier = Modifier.padding(start = 8.dp)) {
        Canvas(modifier = Modifier.size(width = 10.dp, height = 12.dp)) {
            val stroke = 1.dp.toPx()
            val markColor = color.copy(alpha = 0.6f)
            drawRoundRect(markColor, style = Stroke(stroke), cornerRadius = CornerRadius(stroke))
            listOf(0.35f, 0.55f, 0.75f).forEach { y ->
                drawLine(
                    markColor
                    , Offset(size.width * 0.25f, size.height * y)
                    , Offset(size.width * 0.75f, size.height * y)
                    , stroke
                )
            }
        }
    }
}

// A small table with the number of attributes after it.
@Composable
private fun AttributeMark(
    count: Int
    , color: Color
) {
    val markColor = color.copy(alpha = 0.6f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val stroke = 1.dp.toPx()
            drawRect(markColor, style = Stroke(stroke), size = Size(size.width, size.height))
            drawLine(markColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
            drawLine(markColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke)
        }
        BasicText(
            text = count.toString()
            , style = TextStyle(color = markColor, fontSize = 12.sp)
            , modifier = Modifier.padding(start = 3.dp)
        )
    }
}

/** Returns the test tag of the arrow that folds the topic [id] in the outline, or of where it would be. */
fun outlineFoldTag(id: String): String = "com.mindsync.library.outlineFold.$id"

private val ROW_HEIGHT = 28.dp
private val ROW_START = 4.dp
private val INDENT = 24.dp
private val FOLD_ARROW_SIZE = 20.dp
private val BULLET_SIZE = 8.dp
private const val FADED_ALPHA = 0.3f
private val SELECTION_COLOR = Color(0xFF3D8BFD)
