package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.mindsync.library.MindMap
import com.mindsync.library.MindMapState
import com.mindsync.library.data.ArrowLink
import com.mindsync.library.data.NodeAttribute
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.rememberMindMapState
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.library.util.RichText
import com.mindsync.library.util.RichTextLink
import com.mindsync.library.util.RichTextSize
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image

private const val APP_NAME = "MindMapView"
private const val APP_ICON_RESOURCE = "/icon.png"
private const val FREE_MIND_EXTENSION = ".mm"
private const val EMOJI_PICKER_COLUMNS = 8
private const val NOTE_POPUP_DELAY_MILLIS = 1000L

private val EDIT_DIALOG_SIZE = DpSize(600.dp, 720.dp)
private val EDIT_DIALOG_MIN_SIZE = DpSize(480.dp, 620.dp)
private val ATTRIBUTES_DIALOG_SIZE = DpSize(560.dp, 480.dp)
private val ATTRIBUTES_DIALOG_MIN_SIZE = DpSize(420.dp, 320.dp)
private val NOTE_POPUP_MAX_SIZE = DpSize(360.dp, 320.dp)
private val NOTE_POPUP_GAP = 16.dp
private val LINK_MENU_MAX_WIDTH = 480.dp
private const val ARROW_LINK_MENU_LABEL_LENGTH = 30

// The description a new node starts out with is this and the number of the node.
private const val NEW_NODE_DESCRIPTION = "node "
private val NUMBERED_DESCRIPTION = Regex(Regex.escape(NEW_NODE_DESCRIPTION) + "(\\d+)")
private val LINK_DIALOG_FIELD_WIDTH = 400.dp

// The links the app opens. A map may come from elsewhere, so its links are not trusted to start any program.
private val LINK_SCHEMES = listOf("http", "https", "mailto")
private val URL_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

// Wide enough for the longest of the picker labels, so the pickers line up.
private val PICKER_LABEL_WIDTH = 56.dp

private val NODE_COLORS = NodeShape.entries.map { it.defaultColor } + listOf(
    Color(0xFF7AA37D),
    Color(0xFF4B877B),
    Color(0xFF336770),
    Color(0xFF8E7CC3),
    Color(0xFF6FA8DC),
)

private val TEXT_COLORS = listOf(
    Color.Black,
    Color.White,
    Color(0xFF5C5C5C),
    Color(0xFFB3261E),
    Color(0xFF0B5394),
    Color(0xFF274E13),
)

private val ARROW_LINK_COLORS = listOf(
    ArrowLink.DEFAULT_COLOR,
    Color(0xFF5C5C5C),
    Color(0xFFB3261E),
    Color(0xFFC98A4E),
    Color(0xFF274E13),
    Color(0xFF0B5394),
    Color(0xFF8A5FA3),
)

// The arrowheads an arrow link can be given in its menu: which of its two ends has one.
private enum class ArrowHeads(
    val label: String,
    val hasStartArrow: Boolean,
    val hasEndArrow: Boolean,
) {
    FORWARD("\u2192  Forward", false, true),
    BACKWARD("\u2190  Backward", true, false),
    BOTH("\u2194  Both Ways", true, true),
    NONE("\u2014  No Arrowheads", false, false),
}

private val NodeShape.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

private val RichTextSize.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

private sealed interface Operation {
    data class Add(val shape: NodeShape, val side: LayoutSide? = null) : Operation
    data object Update : Operation
}

private class FileActions(
    val new: () -> Unit,
    val open: () -> Unit,
    val save: () -> Unit,
    val saveAs: () -> Unit,
)

private val appIcon: BitmapPainter? by lazy {
    object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE)?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
}

fun main() = application {
    val state = rememberMindMapState()
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var fileMenuExpanded by remember { mutableStateOf(false) }
    // The node an arrow link is being added from, while the node it leads to is being chosen.
    var arrowLinkSourceId by remember { mutableStateOf<String?>(null) }
    var attributesDialogOpen by remember { mutableStateOf(false) }
    // Space is typed in a dialog and chooses the item of a menu, so it folds no node while one is open.
    var isDialogOpen by remember { mutableStateOf(false) }
    // How many nodes were added to the mind map since it was started or opened.
    var createdNodeCount by remember { mutableStateOf(0) }

    fun save(target: File) {
        try {
            target.writeText(FreeMind.write(state.tree))
            file = target
        } catch (e: IOException) {
            error = "Could not save ${target.name}: ${e.message}"
        }
    }

    fun saveAs() {
        chooseFile(frame, "Save As", FileDialog.SAVE, file)?.let { chosen ->
            save(if (chosen.extension.isEmpty()) File(chosen.path + FREE_MIND_EXTENSION) else chosen)
        }
    }

    val fileActions = FileActions(
        new = {
            state.setTree(Tree<Any>())
            file = null
            createdNodeCount = 0
        },
        open = {
            chooseFile(frame, "Open", FileDialog.LOAD, file)?.let { chosen ->
                try {
                    state.setTree(FreeMind.read(chosen.readText()))
                    state.animateTreeChange()
                    file = chosen
                    createdNodeCount = 0
                } catch (e: IOException) {
                    error = "Could not open ${chosen.name}: ${e.message}"
                } catch (e: IllegalArgumentException) {
                    error = "Could not open ${chosen.name}: ${e.message}"
                }
            }
        },
        save = { file?.let { save(it) } ?: saveAs() },
        saveAs = { saveAs() },
    )

    Window(
        onCloseRequest = ::exitApplication
        , icon = appIcon
        , title = file?.let { "${it.name} - $APP_NAME" } ?: APP_NAME
        , onPreviewKeyEvent = { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.isAltPressed && event.key == Key.F -> {
                    fileMenuExpanded = true
                    true
                }
                // FreeMind's own keys for the attributes: show all of them, show none of them and edit them.
                event.isAltPressed && event.key == Key.S -> {
                    state.showAttributes = true
                    true
                }
                event.isAltPressed && event.key == Key.H -> {
                    state.showAttributes = false
                    true
                }
                event.isAltPressed && event.key == Key.F9 -> {
                    attributesDialogOpen = state.selectedNode != null
                    true
                }
                event.key == Key.Escape && arrowLinkSourceId != null -> {
                    arrowLinkSourceId = null
                    true
                }
                !isDialogOpen && error == null && foldCommand(event) != null -> {
                    foldCommand(event)?.run?.invoke(state)
                    true
                }
                !event.isCtrlPressed -> false
                event.key == Key.N -> {
                    fileActions.new()
                    true
                }
                event.key == Key.O -> {
                    fileActions.open()
                    true
                }
                event.key == Key.S -> {
                    if (event.isShiftPressed) fileActions.saveAs() else fileActions.save()
                    true
                }
                event.key == Key.M -> {
                    state.moveNodeToOtherSide()
                    true
                }
                event.key == Key.L -> {
                    arrowLinkSourceId = state.selectedNode?.id
                    true
                }
                else -> false
            }
        }
    ) {
        SideEffect { frame = window }
        MindMapScreen(
            state = state,
            error = error,
            onError = { error = it },
            onErrorDismiss = { error = null },
            fileActions = fileActions,
            fileMenuExpanded = fileMenuExpanded,
            onFileMenuExpandedChange = { fileMenuExpanded = it },
            arrowLinkSourceId = arrowLinkSourceId,
            onArrowLinkSourceIdChange = { arrowLinkSourceId = it },
            createdNodeCount = createdNodeCount,
            onCreatedNodeCountChange = { createdNodeCount = it },
            attributesDialogOpen = attributesDialogOpen,
            onAttributesDialogOpenChange = { attributesDialogOpen = it },
            onDialogOpenChange = { isDialogOpen = it },
        )
    }
}

// FreeMind's own keys for folding.
private class FoldCommand(
    val label: String,
    val shortcut: String,
    val isAlt: Boolean,
    val isCtrl: Boolean,
    val key: Key,
    val run: (MindMapState) -> Unit,
)

private val FOLD_COMMANDS = listOf(
    FoldCommand("Toggle Folded", "Space", isAlt = false, isCtrl = false, Key.Spacebar) { it.toggleFold() },
    FoldCommand("Toggle Children Folded", "Ctrl+Space", isAlt = false, isCtrl = true, Key.Spacebar) { it.toggleChildrenFold() },
    FoldCommand("Fold One Level", "Alt+PageUp", isAlt = true, isCtrl = false, Key.PageUp) { it.foldOneLevel() },
    FoldCommand("Unfold One Level", "Alt+PageDown", isAlt = true, isCtrl = false, Key.PageDown) { it.unfoldOneLevel() },
    FoldCommand("Fold All", "Alt+Home", isAlt = true, isCtrl = false, Key.MoveHome) { it.foldAll() },
    FoldCommand("Unfold All", "Alt+End", isAlt = true, isCtrl = false, Key.MoveEnd) { it.unfoldAll() },
)

private fun foldCommand(event: KeyEvent): FoldCommand? {
    return FOLD_COMMANDS.firstOrNull {
        it.key == event.key && it.isAlt == event.isAltPressed && it.isCtrl == event.isCtrlPressed && !event.isShiftPressed
    }
}

private fun chooseFile(
    window: Frame?,
    title: String,
    mode: Int,
    current: File?,
): File? {
    val dialog = FileDialog(window, title, mode)
    dialog.setFilenameFilter { _, name -> name.endsWith(FREE_MIND_EXTENSION, ignoreCase = true) }
    dialog.directory = current?.parent
    dialog.file = current?.name ?: "*$FREE_MIND_EXTENSION"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

@Composable
private fun MindMapScreen(
    state: MindMapState,
    error: String?,
    onError: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    fileActions: FileActions,
    fileMenuExpanded: Boolean,
    onFileMenuExpandedChange: (Boolean) -> Unit,
    arrowLinkSourceId: String?,
    onArrowLinkSourceIdChange: (String?) -> Unit,
    createdNodeCount: Int,
    onCreatedNodeCountChange: (Int) -> Unit,
    attributesDialogOpen: Boolean,
    onAttributesDialogOpenChange: (Boolean) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    val selectedNode = state.selectedNode
    var operation by remember { mutableStateOf<Operation?>(null) }
    // The side a '+' button was clicked on, while the shape of the node to add there is being chosen.
    var shapeChoiceSide by remember { mutableStateOf<LayoutSide?>(null) }
    // The arrow link its menu is open for, and where in the mind map it was asked for.
    var arrowLinkMenu by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    // The node its menu is open for, and where in the mind map it was asked for.
    var nodeMenu by remember { mutableStateOf<Pair<String, Offset>?>(null) }

    // The note of the hovered node and where the pointer was when it was shown, once the pointer has stayed on the node.
    var notePopup by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val hoveredNode = state.hoveredNode
    val isDialogOpen = operation != null || shapeChoiceSide != null || attributesDialogOpen

    val isMenuOpen = nodeMenu != null || arrowLinkMenu != null
    LaunchedEffect(isDialogOpen, isMenuOpen) { onDialogOpenChange(isDialogOpen || isMenuOpen) }

    val uriHandler = LocalUriHandler.current
    val windowInfo = LocalWindowInfo.current
    val openLink: (String) -> Unit = { url ->
        if (linkScheme(url) !in LINK_SCHEMES) {
            onError("Could not open $url: only web and mail links are opened.")
        }
        else {
            try {
                uriHandler.openUri(url)
            } catch (e: IllegalArgumentException) {
                onError("Could not open $url: ${e.message}")
            }
        }
    }
    LaunchedEffect(hoveredNode?.id, hoveredNode?.note, isDialogOpen) {
        notePopup = null
        val note = hoveredNode?.note?.takeIf { it.isNotBlank() && !isDialogOpen } ?: return@LaunchedEffect
        delay(NOTE_POPUP_DELAY_MILLIS)
        notePopup = note to state.hoverPosition
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    FileMenu(fileActions, fileMenuExpanded, onFileMenuExpandedChange)
                    EditMenu(
                        enabled = selectedNode != null,
                        onAdd = { operation = Operation.Add(it) },
                        onEdit = { operation = Operation.Update },
                        removeEnabled = selectedNode?.parentId != null,
                        onRemove = { state.removeNode() },
                        // Read when the menu opens: moving a node does not change the selected node.
                        moveSide = {
                            selectedNode?.takeIf { it.parentId != null }?.let { node ->
                                if (state.tree.sideOf(node.id) == LayoutSide.LEFT) LayoutSide.RIGHT else LayoutSide.LEFT
                            }
                        },
                        onMove = { state.moveNodeToOtherSide() },
                        // Read when the menu opens, as moveSide is.
                        links = {
                            selectedNode?.let { RichText.links(it.description) + RichText.links(it.note) }.orEmpty()
                        },
                        onOpenLink = openLink,
                        onAddArrowLink = { onArrowLinkSourceIdChange(selectedNode?.id) },
                        onEditAttributes = { onAttributesDialogOpenChange(true) },
                    )
                    ViewMenu(
                        onFit = { state.fitScreen() },
                        showAttributes = state.showAttributes,
                        onShowAttributesChange = { state.showAttributes = it },
                        onFold = { it.run(state) },
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MindMap(
                        state = state
                        , modifier = Modifier.fillMaxSize()
                        // While an arrow link is being added, the next click chooses the node it leads to, or none.
                        , onNodeClick = { node ->
                            arrowLinkSourceId?.let { sourceId ->
                                if (node != null) state.addArrowLink(sourceId, node.id)
                                onArrowLinkSourceIdChange(null)
                            }
                        }
                        , onNodeDoubleClick = { operation = Operation.Update }
                        , onAddChildClick = { _, side ->
                            onArrowLinkSourceIdChange(null)
                            shapeChoiceSide = side
                        }
                        , onArrowLinkMenu = { link, position -> arrowLinkMenu = link.id to position }
                        , onNodeMenu = { node, position ->
                            onArrowLinkSourceIdChange(null)
                            nodeMenu = node.id to position
                        }
                        // A plain click selects a node or starts a double click, so a link takes Ctrl to open.
                        , onLinkClick = { _, url -> if (windowInfo.keyboardModifiers.isCtrlPressed) openLink(url) }
                    )
                    notePopup?.let { (note, position) -> NotePopup(note, position) }
                    if (arrowLinkSourceId != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        ) {
                            Text(
                                "Click the node the arrow should point to. Esc cancels.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    nodeMenu?.let { (nodeId, position) ->
                        state.tree.nodes[nodeId]?.let { node ->
                            NodeMenu(
                                position = position,
                                // The root is on neither side of itself, so it is not moved.
                                side = if (node.parentId == null) null else state.tree.sideOf(nodeId),
                                onEdit = { operation = Operation.Update },
                                onEditAttributes = { onAttributesDialogOpenChange(true) },
                                onLink = { onArrowLinkSourceIdChange(nodeId) },
                                onMove = { state.moveNodeToOtherSide() },
                                // Only a node with children that is not the root is folded.
                                isFolded =
                                    if (node.parentId == null || node.children.isEmpty()) null
                                    else state.tree.isFolded(nodeId),
                                onFold = { state.toggleFold() },
                                onDismiss = { nodeMenu = null },
                            )
                        }
                    }
                    arrowLinkMenu?.let { (linkId, position) ->
                        state.arrowLinks.firstOrNull { it.id == linkId }?.let { link ->
                            ArrowLinkMenu(
                                link = link,
                                position = position,
                                sourceLabel = nodeLabel(state.tree.nodes[link.sourceId]?.description),
                                targetLabel = nodeLabel(state.tree.nodes[link.targetId]?.description),
                                onGoTo = { state.goToNode(it) },
                                onChange = { state.updateArrowLink(it) },
                                onRemove = { state.removeArrowLink(link.id) },
                                onDismiss = { arrowLinkMenu = null },
                            )
                        }
                    }
                }
            }
        }

        error?.let { message ->
            AlertDialog(
                onDismissRequest = onErrorDismiss,
                text = { Text(message) },
                confirmButton = {
                    Button(onClick = onErrorDismiss) {
                        Text("OK")
                    }
                },
            )
        }

        shapeChoiceSide?.let { side ->
            ShapeSelectorDialog(
                onSelect = { shape ->
                    shapeChoiceSide = null
                    operation = Operation.Add(shape, side)
                },
                onDismiss = { shapeChoiceSide = null },
            )
        }

        if (attributesDialogOpen && selectedNode != null) {
            AttributesDialog(
                attributes = remember(selectedNode.id) { state.tree.attributesOf(selectedNode.id) },
                knownNames = remember(selectedNode.id) { attributeNames(state.tree) },
                onSubmit = { state.editNodeAttributes(it) },
                onDismiss = { onAttributesDialogOpenChange(false) },
            )
        }

        operation?.let { currentOperation ->
            val nodeNumber = remember(currentOperation) { nextNodeNumber(state.tree, createdNodeCount) }
            EditNodeDialog(
                title = if (currentOperation is Operation.Update) "Edit node" else "Add node",
                description =
                    if (currentOperation is Operation.Update) selectedNode?.description.orEmpty()
                    else NEW_NODE_DESCRIPTION + nodeNumber,
                // The description of a new node is only a start, so typing replaces it.
                isDescriptionSelected = currentOperation is Operation.Add,
                note = if (currentOperation is Operation.Update) selectedNode?.note.orEmpty() else "",
                color = when (currentOperation) {
                    is Operation.Add -> currentOperation.shape.defaultColor
                    Operation.Update -> selectedNode?.color ?: Color.Unspecified
                },
                textColor = when (currentOperation) {
                    is Operation.Add -> currentOperation.shape.defaultTextColor
                    Operation.Update -> selectedNode?.textColor ?: Color.Unspecified
                },
                onSubmit = { description, note, color, textColor ->
                    when (currentOperation) {
                        is Operation.Add -> {
                            state.addNode(
                                description,
                                currentOperation.shape,
                                color,
                                textColor,
                                currentOperation.side ?: state.layoutSide,
                                note,
                            )
                            onCreatedNodeCountChange(nodeNumber)
                        }
                        Operation.Update -> {
                            state.editNodeText(description)
                            state.editNodeNote(note)
                            state.editNodeColor(color)
                            state.editNodeTextColor(textColor)
                        }
                    }
                },
                onDismiss = { operation = null },
            )
        }
    }
}

// The number of the node that is added next: one more than the nodes there are and the nodes that were
// added, some of which may have been removed since. A map that was opened may hold numbered nodes of
// removed ones too, so the highest number in it counts as well.
private fun nextNodeNumber(
    tree: Tree<*>,
    createdNodeCount: Int,
): Int {
    var nodeCount = 0
    var highestNumber = 0
    tree.doPreorderTraversal { node ->
        if (node.parentId != null) nodeCount++
        val text = RichText.parse(DescriptionEmoji.split(node.description).second).text.trim()
        NUMBERED_DESCRIPTION.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
            highestNumber = maxOf(highestNumber, it)
        }
    }
    return maxOf(nodeCount, highestNumber, createdNodeCount) + 1
}

// Drawn by Compose instead of the window's native menu bar, whose drop down closes as soon as the mouse
// button is released under XWayland.
@Composable
private fun FileMenu(
    actions: FileActions,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box {
        TextButton(onClick = { onExpandedChange(true) }) {
            Text("File")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            FileMenuItem("New", "Ctrl+N", actions.new, onExpandedChange)
            FileMenuItem("Open...", "Ctrl+O", actions.open, onExpandedChange)
            HorizontalDivider()
            FileMenuItem("Save", "Ctrl+S", actions.save, onExpandedChange)
            FileMenuItem("Save As...", "Ctrl+Shift+S", actions.saveAs, onExpandedChange)
        }
    }
}

@Composable
private fun FileMenuItem(
    text: String,
    shortcut: String,
    onClick: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = { Text(shortcut, style = MaterialTheme.typography.labelMedium) },
        onClick = {
            onExpandedChange(false)
            onClick()
        },
    )
}

@Composable
private fun EditMenu(
    enabled: Boolean,
    onAdd: (NodeShape) -> Unit,
    onEdit: () -> Unit,
    removeEnabled: Boolean,
    onRemove: () -> Unit,
    moveSide: () -> LayoutSide?,
    onMove: () -> Unit,
    links: () -> List<RichTextLink>,
    onOpenLink: (String) -> Unit,
    onAddArrowLink: () -> Unit,
    onEditAttributes: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }
    var linksExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Edit")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                addExpanded = false
                linksExpanded = false
            },
        ) {
            Box {
                DropdownMenuItem(
                    text = { Text("Add") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { addExpanded = true },
                    enabled = enabled,
                )
                DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                    NodeShape.entries.forEach { shape ->
                        DropdownMenuItem(
                            text = { Text(shape.label) },
                            onClick = {
                                addExpanded = false
                                expanded = false
                                onAdd(shape)
                            },
                        )
                    }
                }
            }
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    expanded = false
                    onEdit()
                },
                enabled = enabled,
            )
            DropdownMenuItem(
                text = { Text("Attributes...") },
                trailingIcon = { Text("Alt+F9", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onEditAttributes()
                },
                enabled = enabled,
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    expanded = false
                    onRemove()
                },
                enabled = removeEnabled,
            )
            HorizontalDivider()
            val side = moveSide()
            DropdownMenuItem(
                text = { Text(if (side == LayoutSide.LEFT) "Move to Left Side" else "Move to Right Side") },
                trailingIcon = { Text("Ctrl+M", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onMove()
                },
                enabled = side != null,
            )
            DropdownMenuItem(
                text = { Text("Add Arrow Link") },
                trailingIcon = { Text("Ctrl+L", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onAddArrowLink()
                },
                enabled = enabled,
            )
            val nodeLinks = links()
            Box {
                DropdownMenuItem(
                    text = { Text("Open Link") },
                    trailingIcon = { Text("\u25B8") },
                    onClick = { linksExpanded = true },
                    enabled = nodeLinks.isNotEmpty(),
                )
                DropdownMenu(expanded = linksExpanded, onDismissRequest = { linksExpanded = false }) {
                    nodeLinks.forEach { link ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (link.text == link.url) link.url else "${link.text} \u2014 ${link.url}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = LINK_MENU_MAX_WIDTH),
                                )
                            },
                            onClick = {
                                linksExpanded = false
                                expanded = false
                                onOpenLink(link.url)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewMenu(
    onFit: () -> Unit,
    showAttributes: Boolean,
    onShowAttributesChange: (Boolean) -> Unit,
    onFold: (FoldCommand) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("View")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Fit") },
                onClick = {
                    expanded = false
                    onFit()
                },
            )
            HorizontalDivider()
            // The one of the two that is in effect is checked.
            DropdownMenuItem(
                text = { Text("Show All Attributes") },
                leadingIcon = { if (showAttributes) Text("\u2713") },
                trailingIcon = { Text("Alt+S", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onShowAttributesChange(true)
                },
            )
            DropdownMenuItem(
                text = { Text("Show No Attributes") },
                leadingIcon = { if (!showAttributes) Text("\u2713") },
                trailingIcon = { Text("Alt+H", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onShowAttributesChange(false)
                },
            )
            HorizontalDivider()
            FOLD_COMMANDS.forEach { command ->
                DropdownMenuItem(
                    text = { Text(command.label) },
                    trailingIcon = { Text(command.shortcut, style = MaterialTheme.typography.labelMedium) },
                    onClick = {
                        expanded = false
                        onFold(command)
                    },
                )
            }
        }
    }
}

@Composable
private fun ShapeSelectorDialog(
    onSelect: (NodeShape) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shape") },
        text = {
            Column {
                NodeShape.entries.forEach { shape ->
                    DropdownMenuItem(text = { Text(shape.label) }, onClick = { onSelect(shape) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNodeDialog(
    title: String,
    description: String,
    isDescriptionSelected: Boolean,
    note: String,
    color: Color,
    textColor: Color,
    onSubmit: (description: String, note: String, color: Color, textColor: Color) -> Unit,
    onDismiss: () -> Unit,
) {
    // The emoji in front of the description is picked on its own, so it is kept out of the editor.
    val (emoji, text) = remember { DescriptionEmoji.split(description) }
    val richTextState = remember {
        RichTextState().setHtml(text).apply {
            if (isDescriptionSelected) selection = TextRange(0, annotatedString.text.length)
        }
    }
    val descriptionFocusRequester = remember { FocusRequester() }
    val noteState = remember { RichTextState().setHtml(note) }
    var selectedEmoji by remember { mutableStateOf(emoji) }
    var selectedColor by remember { mutableStateOf(color) }
    var selectedTextColor by remember { mutableStateOf(textColor) }

    // A window of its own, so it can be resized: the note takes the room the rest leaves.
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = EDIT_DIALOG_SIZE),
        title = title,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = with(EDIT_DIALOG_MIN_SIZE) { Dimension(width.value.toInt(), height.value.toInt()) }
            descriptionFocusRequester.requestFocus()
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                EmojiPicker(selectedEmoji = selectedEmoji, onSelect = { selectedEmoji = it })
                RichTextToolbar(richTextState)
                OutlinedRichTextEditor(
                    state = richTextState,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp)
                        .focusRequester(descriptionFocusRequester),
                )
                ColorPicker(
                    label = "Fill",
                    colors = remember(color) { (NODE_COLORS + color).distinct() },
                    selectedColor = selectedColor,
                    onSelect = { selectedColor = it },
                )
                ColorPicker(
                    label = "Text",
                    colors = remember(textColor) { (TEXT_COLORS + textColor).distinct() },
                    selectedColor = selectedTextColor,
                    onSelect = { selectedTextColor = it },
                )
                Text("Note", style = MaterialTheme.typography.titleSmall)
                RichTextToolbar(noteState)
                OutlinedRichTextEditor(
                    state = noteState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSubmit(
                                DescriptionEmoji.prepend(selectedEmoji, richTextState.toHtml()),
                                // An editor that was emptied still holds an empty paragraph, which is no note.
                                if (noteState.annotatedString.text.isBlank()) "" else noteState.toHtml(),
                                selectedColor,
                                selectedTextColor,
                            )
                            onDismiss()
                        },
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// The text of a node as a menu shows it: without its markup, on one line and cut short.
private fun nodeLabel(description: String?): String {
    val text = RichText.parse(description.orEmpty()).text.replace(Regex("\\s+"), " ").trim()
    return if (text.length > ARROW_LINK_MENU_LABEL_LENGTH) text.take(ARROW_LINK_MENU_LABEL_LENGTH) + "\u2026" else text
}

// The menu of a node, which floats where the node was clicked. The click selected the node, which is the
// one the items act on. Each of them closes the menu.
@Composable
private fun NodeMenu(
    position: Offset,
    side: LayoutSide?,
    onEdit: () -> Unit,
    onEditAttributes: () -> Unit,
    onLink: () -> Unit,
    onMove: () -> Unit,
    isFolded: Boolean?,
    onFold: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    onDismiss()
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Attributes") },
                onClick = {
                    onDismiss()
                    onEditAttributes()
                },
            )
            DropdownMenuItem(
                text = { Text("Link") },
                onClick = {
                    onDismiss()
                    onLink()
                },
            )
            if (side != null) {
                DropdownMenuItem(
                    text = { Text("Move") },
                    // The arrow is the way the node goes: to the side of the root it is not on.
                    trailingIcon = { Text(if (side == LayoutSide.RIGHT) "\u2190" else "\u2192") },
                    onClick = {
                        onDismiss()
                        onMove()
                    },
                )
            }
            if (isFolded != null) {
                DropdownMenuItem(
                    text = { Text(if (isFolded) "Unfold" else "Fold") },
                    onClick = {
                        onDismiss()
                        onFold()
                    },
                )
            }
        }
    }
}

// The menu of an arrow link, which floats where the link was clicked. Each of its items closes it.
@Composable
private fun ArrowLinkMenu(
    link: ArrowLink,
    position: Offset,
    sourceLabel: String,
    targetLabel: String,
    onGoTo: (nodeId: String) -> Unit,
    onChange: (ArrowLink) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A drop down menu opens at the box it is in, so an empty box is put where the click was.
    Box(modifier = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text("Go to: $sourceLabel") },
                onClick = {
                    onDismiss()
                    onGoTo(link.sourceId)
                },
            )
            DropdownMenuItem(
                text = { Text("Go to: $targetLabel") },
                onClick = {
                    onDismiss()
                    onGoTo(link.targetId)
                },
            )
            HorizontalDivider()
            ArrowHeads.entries.forEach { heads ->
                val isCurrent = heads.hasStartArrow == link.hasStartArrow && heads.hasEndArrow == link.hasEndArrow
                DropdownMenuItem(
                    text = { Text(heads.label) },
                    trailingIcon = { if (isCurrent) Text("\u2713") },
                    onClick = {
                        onDismiss()
                        onChange(link.copy(hasStartArrow = heads.hasStartArrow, hasEndArrow = heads.hasEndArrow))
                    },
                )
            }
            HorizontalDivider()
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ColorPicker(
                    label = "Color",
                    colors = remember(link.color) { (ARROW_LINK_COLORS + link.color).distinct() },
                    selectedColor = link.color,
                    onSelect = {
                        onDismiss()
                        onChange(link.copy(color = it))
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove Arrow Link") },
                onClick = {
                    onDismiss()
                    onRemove()
                },
            )
        }
    }
}

// The names of the attributes of all nodes of the map: an attribute is given to a node, but its name is
// one of the map.
private fun attributeNames(tree: Tree<*>): List<String> {
    val names = sortedSetOf<String>()
    tree.doPreorderTraversal { node -> tree.attributesOf(node.id).forEach { names.add(it.name) } }
    return names.toList()
}

@Composable
private fun AttributesDialog(
    attributes: List<NodeAttribute>,
    knownNames: List<String>,
    onSubmit: (List<NodeAttribute>) -> Unit,
    onDismiss: () -> Unit,
) {
    // A node without attributes starts with an empty row to fill in.
    val rows = remember {
        mutableStateListOf<NodeAttribute>().apply { addAll(attributes.ifEmpty { listOf(NodeAttribute("", "")) }) }
    }
    val unusedNames = knownNames.filter { name -> rows.none { it.name == name } }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = ATTRIBUTES_DIALOG_SIZE),
        title = "Attributes",
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize =
                with(ATTRIBUTES_DIALOG_MIN_SIZE) { Dimension(width.value.toInt(), height.value.toInt()) }
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    rows.forEachIndexed { index, attribute ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = attribute.name,
                                onValueChange = { rows[index] = attribute.copy(name = it) },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = attribute.value,
                                onValueChange = { rows[index] = attribute.copy(value = it) },
                                label = { Text("Value") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f),
                            )
                            TextButton(onClick = { rows.removeAt(index) }) {
                                Text("\u2715")
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { rows.add(NodeAttribute("", "")) }) {
                            Text("+ Add attribute")
                        }
                        // The names other nodes have attributes of, each of which adds a row of that name.
                        unusedNames.forEach { name ->
                            TextButton(onClick = { rows.add(NodeAttribute(name, "")) }) {
                                Text("+ $name")
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            // A row that was given no name is no attribute.
                            onSubmit(rows.map { it.copy(name = it.name.trim()) }.filter { it.name.isNotEmpty() })
                            onDismiss()
                        },
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// The popup takes no pointer input, so the pointer stays over the mind map below it while it is shown.
@Composable
private fun NotePopup(
    note: String,
    position: Offset,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(note, linkColor) { RichText.parse(note, linkColor) }
    val shape = RoundedCornerShape(8.dp)

    Layout(
        content = {
            Box(
                modifier = Modifier
                    .widthIn(max = NOTE_POPUP_MAX_SIZE.width)
                    .heightIn(max = NOTE_POPUP_MAX_SIZE.height)
                    .shadow(4.dp, shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .padding(12.dp),
            ) {
                Text(text, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = NOTE_POPUP_GAP.roundToPx()
        val pointerX = position.x.roundToInt()
        val pointerY = position.y.roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Below and to the right of the pointer, or above it where there is no room below.
            val x = (pointerX + gap).coerceAtMost(constraints.maxWidth - placeable.width).coerceAtLeast(0)
            val below = pointerY + gap
            val y =
                if (below + placeable.height <= constraints.maxHeight) below
                else (pointerY - gap - placeable.height).coerceAtLeast(0)
            placeable.place(x, y)
        }
    }
}

@Composable
private fun RichTextToolbar(state: RichTextState) {
    val spanStyle = state.currentSpanStyle
    val decoration = spanStyle.textDecoration ?: TextDecoration.None
    var linkDialogOpen by remember { mutableStateOf(false) }

    if (linkDialogOpen) {
        LinkDialog(
            url = state.selectedLinkUrl,
            onSubmit = { state.setLink(it) },
            onRemove = { state.removeLink() },
            onDismiss = { linkDialogOpen = false },
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RichTextToggle(
            label = "B",
            labelStyle = SpanStyle(fontWeight = FontWeight.Bold),
            checked = spanStyle.fontWeight == FontWeight.Bold,
            onToggle = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
        )
        RichTextToggle(
            label = "I",
            labelStyle = SpanStyle(fontStyle = FontStyle.Italic),
            checked = spanStyle.fontStyle == FontStyle.Italic,
            onToggle = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
        )
        RichTextToggle(
            label = "U",
            labelStyle = SpanStyle(textDecoration = TextDecoration.Underline),
            checked = TextDecoration.Underline in decoration,
            onToggle = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
        )
        RichTextToggle(
            label = "S",
            labelStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
            checked = TextDecoration.LineThrough in decoration,
            onToggle = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) },
        )
        RichTextToggle(
            label = "\u2022",
            labelStyle = SpanStyle(),
            checked = state.isUnorderedList,
            onToggle = { state.toggleUnorderedList() },
        )
        RichTextToggle(
            label = "1.",
            labelStyle = SpanStyle(),
            checked = state.isOrderedList,
            onToggle = { state.toggleOrderedList() },
        )
        RichTextToggle(
            label = "\uD83D\uDD17",
            labelStyle = SpanStyle(),
            checked = state.isLink,
            onToggle = { linkDialogOpen = true },
        )
        RichTextSizeSelect(
            selectedSize = RichTextSize.of(spanStyle.fontSize),
            onSelect = { state.setSize(it) },
        )
    }
}

// The link the cursor is in is changed; otherwise the selection becomes a link, or the URL is put in as one.
private fun RichTextState.setLink(url: String) {
    when {
        isLink -> updateLink(url)
        !selection.collapsed -> addLinkToSelection(url)
        else -> addLink(text = url, url = url)
    }
}

private fun linkScheme(url: String): String? = URL_SCHEME.find(url)?.groupValues?.get(1)?.lowercase()

// An address typed without a scheme is taken to be a mail address when it looks like one, and a web address otherwise.
private fun completeUrl(input: String): String {
    val url = input.trim()
    return when {
        url.isEmpty() || linkScheme(url) != null -> url
        '@' in url && '/' !in url -> "mailto:$url"
        else -> "https://$url"
    }
}

@Composable
private fun LinkDialog(
    url: String?,
    onSubmit: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(url.orEmpty()) }
    val completed = completeUrl(input)
    val isOpenable = linkScheme(completed) in LINK_SCHEMES
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (url == null) "Add link" else "Edit link") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Address") },
                placeholder = { Text("https://") },
                singleLine = true,
                isError = input.isNotBlank() && !isOpenable,
                supportingText = {
                    if (input.isNotBlank() && !isOpenable) Text("Only web and mail addresses can be linked to.")
                },
                modifier = Modifier.width(LINK_DIALOG_FIELD_WIDTH).focusRequester(focusRequester),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(completed)
                    onDismiss()
                },
                enabled = isOpenable,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (url != null) {
                    TextButton(
                        onClick = {
                            onRemove()
                            onDismiss()
                        },
                    ) {
                        Text("Remove link")
                    }
                }
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

private fun RichTextState.setSize(size: RichTextSize) {
    // A size is only taken away by the very size the text has, so a selection is first given one size throughout.
    if (!selection.collapsed) addSpanStyle(SpanStyle(fontSize = 1.em))
    removeSpanStyle(SpanStyle(fontSize = currentSpanStyle.fontSize))
    if (size != RichTextSize.NORMAL) addSpanStyle(size.spanStyle)
}

// Like the toggles, the select does not take focus, so the editor keeps its selection while a size is chosen.
@Composable
private fun RichTextSizeSelect(
    selectedSize: RichTextSize,
    onSelect: (RichTextSize) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.focusProperties { canFocus = false },
        ) {
            Text("${selectedSize.label} \u25BE")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
        ) {
            RichTextSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(size.label, style = LocalTextStyle.current.merge(size.spanStyle)) },
                    onClick = {
                        expanded = false
                        onSelect(size)
                    },
                )
            }
        }
    }
}

// The toggles do not take focus, so the editor keeps its selection while a style is applied.
@Composable
private fun RichTextToggle(
    label: String,
    labelStyle: SpanStyle,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = Modifier.focusProperties { canFocus = false },
    ) {
        Text(label, style = LocalTextStyle.current.merge(labelStyle))
    }
}

@Composable
private fun EmojiPicker(
    selectedEmoji: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Emoji", maxLines = 1, softWrap = false, modifier = Modifier.width(PICKER_LABEL_WIDTH))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selectedEmoji ?: "None")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    },
                )
                HorizontalDivider()
                DescriptionEmoji.EMOJIS.chunked(EMOJI_PICKER_COLUMNS).forEach { emojis ->
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        emojis.forEach { emoji ->
                            val borderColor =
                                if (emoji == selectedEmoji) MaterialTheme.colorScheme.onSurface else Color.Transparent
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, borderColor, CircleShape)
                                    .clickable {
                                        expanded = false
                                        onSelect(emoji)
                                    },
                            ) {
                                Text(emoji)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(
    label: String,
    colors: List<Color>,
    selectedColor: Color,
    onSelect: (Color) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(PICKER_LABEL_WIDTH))
        colors.forEach { color ->
            val borderColor =
                if (color == selectedColor) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (color == selectedColor) 3.dp else 1.dp, borderColor, CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}
