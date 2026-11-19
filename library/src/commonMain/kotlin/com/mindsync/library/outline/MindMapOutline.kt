package com.mindsync.library.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.mindsync.library.MindMapState
import com.mindsync.library.TextEditHost
import com.mindsync.library.data.NodeData
import com.mindsync.library.data.TaskDate
import com.mindsync.library.model.TopicKind

/** The test tag of the outline. */
const val OUTLINE_TAG = "com.mindsync.library.outline"

/** Returns the test tag of the row of the topic [id] in the outline. */
fun outlineRowTag(id: String): String = "com.mindsync.library.outlineRow.$id"

/**
 * The map of [state] as an outline: a row for each topic shown, indented by how far below the root it is, in the
 * order of [outlineRows]. It is the same map as [com.mindsync.library.MindMap] shows from the same [state], so the
 * selection, folding, undo and the task filter are those of the map, and a change in either shows in both.
 *
 * A click on a row selects its topic, with Ctrl adds it to the selection or takes it out, and with Shift selects a
 * range, as on the map, and then calls [onRowClick] with the topic, as a click on a node calls
 * [com.mindsync.library.MindMap]'s `onNodeClick`. A click on the arrow before a topic folds or unfolds it. A second
 * click soon after calls [onRowDoubleClick], a click on a link in the text [onLinkClick], and a right click [onRowMenu] with where it was,
 * in the outline. The selected topic's row is scrolled into view whenever the selection changes. While the pointer
 * is on the note or attribute mark of a row, [onNoteHover] or [onAttributeHover] is called with the topic and where
 * the pointer is, in the outline, for showing them, and with null once it leaves the mark.
 *
 * Each row shows the topic's fill as a bullet, its priority and progress, its text in the font of its kind from
 * [topicFontFamilies] with emojis in [emojiFontFamily], whether it has a note and how many attributes it has, and its
 * due date by [dateLabel]. Topics the task filter fades are faded here too.
 *
 * Using the outline makes it [MindMapState.textEditHost], so a topic's text is typed in its row, with the keys of an
 * outliner: Enter ends the typing and goes on in a sibling below, which [onAddSiblingToType] adds and starts typing
 * in; Tab and Shift+Tab indent and outdent the topic, Alt+Shift+Up and Down move it, and Backspace in an empty topic
 * removes it and goes on at the end of the row above. All of it is undone a topic at a time.
 */
@Composable
fun MindMapOutline(
    state: MindMapState
    , modifier: Modifier = Modifier
    , topicFontFamilies: Map<TopicKind, FontFamily?> = emptyMap()
    , emojiFontFamily: FontFamily? = null
    , onRowClick: (node: NodeData<*>) -> Unit = {}
    , onRowDoubleClick: (node: NodeData<*>) -> Unit = { state.startTextEdit() }
    , onAddSiblingToType: (isAbove: Boolean) -> Unit = { isAbove ->
        state.addNodeForTextEdit { addSiblingNode("", isAbove = isAbove) }
    }
    , onLinkClick: (node: NodeData<*>, url: String) -> Unit = { _, _ -> }
    , onRowMenu: (node: NodeData<*>, position: Offset) -> Unit = { _, _ -> }
    , onNoteHover: (node: NodeData<*>?, position: Offset) -> Unit = { _, _ -> }
    , onAttributeHover: (node: NodeData<*>?, position: Offset) -> Unit = { _, _ -> }
    , onFocusChange: (isFocused: Boolean) -> Unit = {}
    , focusRequester: FocusRequester = remember { FocusRequester() }
    , dateLabel: (date: TaskDate) -> String = { it.toIsoString() }
) {
    // The tree is no state of its own; the map is drawn again on each change of it, and so is the outline.
    state.drawTick
    val rows = outlineRows(state.tree)
    val listState = rememberLazyListState()
    val selectedIds = state.selectedNodes.map { it.id }.toSet()
    val selectedId = state.selectedNode?.id
    val fadedIds = state.taskFilterView().fadedIds
    val textColor = if (state.isDark) DARK_TEXT else LIGHT_TEXT
    val currentOnRowClick by rememberUpdatedState(onRowClick)
    val currentOnRowDoubleClick by rememberUpdatedState(onRowDoubleClick)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnRowMenu by rememberUpdatedState(onRowMenu)
    val currentOnNoteHover by rememberUpdatedState(onNoteHover)
    val currentOnAttributeHover by rememberUpdatedState(onAttributeHover)
    var outlineCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val clicks = remember { OutlineClicks() }

    LaunchedEffect(selectedId) {
        val index = rows.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val isInView = item != null && item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset
        if (!isInView) listState.animateScrollToItem(index)
    }

    LazyColumn(
        state = listState
        , modifier = modifier
            .background(state.background)
            .onGloballyPositioned { outlineCoordinates = it }
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.hasFocus) state.textEditHost = TextEditHost.OUTLINE
                onFocusChange(it.hasFocus)
            }
            .focusable()
            .testTag(OUTLINE_TAG)
    ) {
        items(rows, key = { it.id }) { row ->
            OutlineRowView(
                state = state
                , row = row
                , isSelected = row.id in selectedIds
                , isFaded = row.id in fadedIds
                , textColor = textColor
                , topicFontFamily = topicFontFamilies[state.tree.topicKindOf(row.id)]
                , emojiFontFamily = emojiFontFamily
                , dateLabel = dateLabel
                , onAddSiblingToType = onAddSiblingToType
                , onPress = { node, press ->
                    state.textEditHost = TextEditHost.OUTLINE
                    focusRequester.requestFocus()
                    when {
                        press.isSecondary -> {
                            state.onNodeMenuClick(node)
                            val position = outlineCoordinates?.localPositionOf(press.coordinates, press.position)
                            currentOnRowMenu(state.selectedNode ?: node, position ?: press.position)
                        }
                        press.isShift -> {
                            state.selectRange(node.id)
                            currentOnRowClick(node)
                        }
                        press.isCtrl && press.link == null -> {
                            state.toggleSelected(node.id)
                            currentOnRowClick(node)
                        }
                        else -> {
                            state.select(node)
                            currentOnRowClick(node)
                            press.link?.let { currentOnLinkClick(node, it) }
                            if (clicks.isSecondClick(node.id, press.uptimeMillis, press.doubleClickMillis)) {
                                currentOnRowDoubleClick(node)
                            }
                        }
                    }
                }
                , onFoldClick = { node ->
                    focusRequester.requestFocus()
                    state.onFoldMarkerClick(node)
                }
                , onMarkHover = { mark, coordinates, position ->
                    val node = coordinates?.let { state.tree.nodes[row.id] }
                    val inOutline = coordinates
                        ?.let { outlineCoordinates?.localPositionOf(it, position) }
                        ?: position
                    when (mark) {
                        OutlineMark.NOTE -> currentOnNoteHover(node, inOutline)
                        OutlineMark.ATTRIBUTES -> currentOnAttributeHover(node, inOutline)
                    }
                }
            )
        }
    }
}

// A second click on the same row soon after the first is a double click, and a third one starts again.
private class OutlineClicks {
    private var lastId: String? = null
    private var lastTime = 0L

    fun isSecondClick(
        id: String
        , time: Long
        , timeout: Long
    ): Boolean {
        val isSecond = lastId == id && time - lastTime <= timeout
        lastId = if (isSecond) null else id
        lastTime = time
        return isSecond
    }
}

internal val LIGHT_TEXT = Color(0xFF1F1F1F)
internal val DARK_TEXT = Color(0xFFE8E8E8)
