package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.FileActions
import com.FileMenu
import com.mindsync.library.MindMap
import com.mindsync.library.MindMapState
import com.mindsync.library.data.*
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.HtmlOutline
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.rememberMindMapState
import com.mindsync.library.util.DescriptionEmoji
import com.mindsync.library.util.RichText
import com.mindsync.library.util.RichTextSize
import com.mindsync.mindmapview.desktop.component.ColorPicker
import com.mindsync.mindmapview.desktop.dialog.*
import com.mindsync.mindmapview.desktop.menuBar.EditMenu
import com.mindsync.mindmapview.desktop.menuBar.ViewMenu
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import com.mohamedrejeb.richeditor.ui.material3.RichText as RichTextView

internal const val APP_NAME = "MindMapView"
private const val APP_ICON_RESOURCE = "/icon.png"
private const val NOTE_POPUP_DELAY_MILLIS = 1000L
private const val SUCCESS_MESSAGE_MILLIS = 3000L

val EDIT_DIALOG_SIZE = DpSize(600.dp, 770.dp)
val EDIT_DIALOG_MIN_WIDTH = 480.dp
const val NOTE_LINES = 4
// The room an outlined text field leaves above and below its text.
val TEXT_FIELD_VERTICAL_PADDING = 32.dp
private val ATTRIBUTES_DIALOG_SIZE = DpSize(560.dp, 480.dp)
private val ATTRIBUTES_DIALOG_MIN_SIZE = DpSize(420.dp, 320.dp)
private val NOTE_POPUP_MAX_SIZE = DpSize(360.dp, 320.dp)
private val NOTE_POPUP_GAP = 16.dp
val LINK_MENU_MAX_WIDTH = 480.dp
private const val ARROW_LINK_MENU_LABEL_LENGTH = 30

// The description a new node starts out with is this and the number of the node.
private const val NEW_NODE_DESCRIPTION = "node "
private val NUMBERED_DESCRIPTION = Regex(Regex.escape(NEW_NODE_DESCRIPTION) + "(\\d+)")
val LINK_DIALOG_FIELD_WIDTH = 400.dp

// The links the app opens. A map may come from elsewhere, so its links are not trusted to start any program.
val LINK_SCHEMES = listOf("http", "https", "mailto")
private val URL_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

// The fill colors offered for a node until the user chooses their own in the preferences.
val DEFAULT_NODE_COLORS = NodeShape.entries.map { it.defaultColor } + listOf(
    Color(0xFF7AA37D),
    Color(0xFF4B877B),
    Color(0xFF336770),
    Color(0xFF8E7CC3),
    Color(0xFF6FA8DC),
)

// The text colors offered for a node until the user chooses their own in the preferences.
val DEFAULT_TEXT_COLORS = listOf(
    Color.Black,
    Color.White,
    Color(0xFF5C5C5C),
    Color(0xFFB3261E),
    Color(0xFF0B5394),
    Color(0xFF274E13),
)

// The arrow link colors offered until the user chooses their own in the preferences.
val DEFAULT_ARROW_LINK_COLORS = listOf(
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

val NodeShape.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

val RichTextSize.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

private sealed interface Operation {
    // A shape that is not chosen is that of the last child of the node the new one is added to.
    data class Add(val shape: NodeShape? = null, val side: LayoutSide? = null) : Operation
    data object Update : Operation
}

// Not a data class, so that the same message shown again is a new one and is shown for its full time again.
private class SuccessMessage(val text: String)

private val appIcon: BitmapPainter? by lazy {
    object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE)?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
}

fun main() = application {
    val state = rememberMindMapState()
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<SuccessMessage?>(null) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var fileMenuExpanded by remember { mutableStateOf(false) }
    // The node an arrow link is being added from, while the node it leads to is being chosen.
    var arrowLinkSourceId by remember { mutableStateOf<String?>(null) }
    var attributesDialogOpen by remember { mutableStateOf(false) }
    // Space is typed in a dialog and chooses the item of a menu, so it folds no node while one is open.
    var isDialogOpen by remember { mutableStateOf(false) }
    // How many nodes were added to the mind map since it was started or opened.
    var createdNodeCount by remember { mutableStateOf(0) }
    // The text to find, which is kept when the find bar is closed for F3 to find it again.
    var findText by remember { mutableStateOf("") }
    var isFindOpen by remember { mutableStateOf(false) }
    // While the find bar has the focus, keys are typed into it rather than acting on the map.
    var isFindFocused by remember { mutableStateOf(false) }
    // Each change of it has the find bar take the focus, with its text selected.
    var findFocusRequest by remember { mutableStateOf(0) }
    // What waits for the unsaved changes to be saved or not, while that is asked.
    var afterSaveChoice by remember { mutableStateOf<(() -> Unit)?>(null) }

    val recentFiles = remember { RecentFiles.ofUser() }
    var recentPaths by remember { mutableStateOf(recentFiles.paths) }

    val preferencesStore = remember { UserPreferencesStore.ofUser() }
    var preferences by remember { mutableStateOf(preferencesStore.preferences) }

    // Returns whether the mind map is saved.
    fun save(target: File): Boolean {
        try {
            target.writeText(MindMapViewFile.write(state.tree))
            state.markSaved()
            // A mind map that is saved under a new name is one to open again, as one that was opened is.
            if (target != file) recentPaths = recentFiles.add(target)
            file = target
            successMessage = SuccessMessage("Saved ${target.name}")
            return true
        } catch (e: IOException) {
            error = "Could not save ${target.name}: ${e.message}"
            return false
        }
    }

    // Returns whether the mind map is saved: no file may be chosen to save it to. A map opened from a FreeMind
    // file is saved in this app's own format, next to that file.
    fun saveAs(): Boolean {
        val suggested = file?.let { mindMapViewFileFor(it) }
        val chosen = chooseFile(frame, "Save As", FileDialog.SAVE, suggested) ?: return false
        return save(mindMapViewFileFor(withExtension(chosen, MIND_MAP_VIEW_EXTENSION)))
    }

    // An export is no save of the mind map, so the changes it has are as unsaved as they were.
    fun export(
        extension: String,
        write: (File) -> Unit,
    ) {
        val suggested = File(file?.parent, exportFileName(file?.name, extension))
        val chosen = chooseFile(frame, "Export", FileDialog.SAVE, suggested, listOf(extension)) ?: return
        val target = withExtension(chosen, extension)
        try {
            write(target)
        } catch (e: IOException) {
            error = "Could not export ${target.name}: ${e.message}"
        }
    }

    fun saveMap(): Boolean = file?.takeIf { isSavedTo(it) }?.let { save(it) } ?: saveAs()

    // The unsaved changes of the mind map would be lost, so whether to save them is asked first.
    fun afterSavingChanges(action: () -> Unit) {
        if (state.isModified) afterSaveChoice = action else action()
    }

    // A mind map that cannot be opened is no recent file any longer.
    fun open(chosen: File) {
        try {
            state.setTree(readMap(chosen.name, chosen.readText()))
            state.animateTreeChange()
            file = chosen
            createdNodeCount = 0
            recentPaths = recentFiles.add(chosen)
        } catch (e: IOException) {
            error = "Could not open ${chosen.name}: ${e.message}"
            recentPaths = recentFiles.remove(chosen)
        } catch (e: IllegalArgumentException) {
            error = "Could not open ${chosen.name}: ${e.message}"
            recentPaths = recentFiles.remove(chosen)
        }
    }

    fun showFind() {
        isFindOpen = true
        findFocusRequest++
    }

    // Goes from the selected node to the match in [direction]: a node clicked meanwhile is where it goes on from.
    fun find(
        direction: FindDirection,
        fromId: String? = state.selectedNode?.id,
    ) {
        val matchIds = state.findNodes(findText).map { it.id }
        nextMatch(state.tree, matchIds, fromId, direction)?.let { state.goToNode(it) }
    }

    val fileActions = FileActions(
        new = {
            afterSavingChanges {
                state.setTree(Tree<Any>())
                file = null
                createdNodeCount = 0
            }
        },
        open = {
            afterSavingChanges {
                chooseFile(frame, "Open", FileDialog.LOAD, file, OPENABLE_EXTENSIONS)?.let { open(it) }
            }
        },
        openRecent = { afterSavingChanges { open(it) } },
        save = { saveMap() },
        saveAs = { saveAs() },
        exportImage = {
            export(PNG_EXTENSION) { target ->
                writePng(state.exportImage() ?: throw IOException("the mind map is not shown yet"), target)
            }
        },
        exportHtml = { export(HTML_EXTENSION) { it.writeText(HtmlOutline.write(state.tree)) } },
        exportFreeMind = { export(FREE_MIND_EXTENSION) { it.writeText(FreeMind.write(state.tree)) } },
    )

    Window(
        onCloseRequest = { afterSavingChanges(::exitApplication) }
        , icon = appIcon
        , title = windowTitle(file?.name, state.isModified)
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
                !isDialogOpen && !isFindFocused && error == null && foldCommand(event) != null -> {
                    foldCommand(event)?.run?.invoke(state)
                    true
                }
                // F3 finds again after the find bar is closed, as FreeMind's Find Next does.
                !isDialogOpen && error == null && event.key == Key.F3 -> {
                    if (findText.isBlank()) showFind()
                    else find(if (event.isShiftPressed) FindDirection.PREVIOUS else FindDirection.NEXT)
                    true
                }
                !event.isCtrlPressed -> false
                !isDialogOpen && error == null && event.key == Key.F -> {
                    showFind()
                    true
                }
                // A dialog is about the mind map as it is, so neither another one nor a change of it comes in between.
                !isDialogOpen && error == null && event.key == Key.N -> {
                    fileActions.new()
                    true
                }
                !isDialogOpen && error == null && event.key == Key.O -> {
                    fileActions.open()
                    true
                }
                !isDialogOpen && error == null && event.key == Key.S -> {
                    if (event.isShiftPressed) fileActions.saveAs() else fileActions.save()
                    true
                }
                !isDialogOpen && error == null && event.key == Key.M -> {
                    state.moveNodeAround(isClockwise = !event.isShiftPressed)
                    true
                }
                !isDialogOpen && error == null && event.key == Key.L -> {
                    arrowLinkSourceId = state.selectedNode?.id
                    true
                }
                // A dialog may have text of its own to copy and paste, and to select all of.
                !isDialogOpen && !isFindFocused && error == null && event.key == Key.A -> {
                    state.selectAll()
                    true
                }
                !isDialogOpen && !isFindFocused && error == null && event.key == Key.C -> {
                    state.copyNodeToClipboard()
                    true
                }
                // A node that is cut can be pasted back, so it is removed without being confirmed.
                !isDialogOpen && !isFindFocused && error == null && event.key == Key.X -> {
                    state.cutNodeToClipboard()
                    true
                }
                !isDialogOpen && !isFindFocused && error == null && event.key == Key.V -> {
                    state.pasteFromClipboard(preferences)
                    true
                }
                // A dialog's text fields undo their own typing.
                !isDialogOpen && !isFindFocused && error == null && event.key == Key.Z -> {
                    if (event.isShiftPressed) state.redo() else state.undo()
                    true
                }
                !isDialogOpen && !isFindFocused && error == null && event.key == Key.Y -> {
                    state.redo()
                    true
                }
                else -> false
            }
        }
        // Escape closes an open menu first, which takes the key, so only an Escape nothing else took gets here.
        , onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && !isDialogOpen && error == null) {
                state.select(null)
                true
            }
            else {
                false
            }
        }
    ) {
        SideEffect { frame = window }
        MindMapScreen(
            state = state,
            error = error,
            onError = { error = it },
            onErrorDismiss = { error = null },
            successMessage = successMessage,
            onSuccessMessageDismiss = { successMessage = null },
            fileActions = fileActions,
            recentPaths = recentPaths,
            fileMenuExpanded = fileMenuExpanded,
            onFileMenuExpandedChange = { fileMenuExpanded = it },
            arrowLinkSourceId = arrowLinkSourceId,
            onArrowLinkSourceIdChange = { arrowLinkSourceId = it },
            createdNodeCount = createdNodeCount,
            onCreatedNodeCountChange = { createdNodeCount = it },
            attributesDialogOpen = attributesDialogOpen,
            onAttributesDialogOpenChange = { attributesDialogOpen = it },
            onDialogOpenChange = { isDialogOpen = it },
            preferences = preferences,
            onPreferencesChange = { preferences = preferencesStore.update(it) },
            saveQuestion = afterSaveChoice?.let { saveChangesQuestion(file?.name) },
            onSaveChoice = { choice ->
                val action = afterSaveChoice
                afterSaveChoice = null
                if (goesOn(choice) { saveMap() }) action?.invoke()
            },
            onFind = { showFind() },
            findBar = {
                if (isFindOpen) {
                    val matchIds = state.findNodes(findText).map { it.id }
                    FindBar(
                        text = findText,
                        onTextChange = {
                            findText = it
                            find(FindDirection.NEXT, fromId = null)
                        },
                        countText = findCountText(matchIds, state.selectedNode?.id),
                        hasMatches = matchIds.isNotEmpty(),
                        focusRequest = findFocusRequest,
                        onFocusChange = { isFindFocused = it },
                        onNext = { find(FindDirection.NEXT) },
                        onPrevious = { find(FindDirection.PREVIOUS) },
                        onClose = {
                            isFindOpen = false
                            isFindFocused = false
                        },
                    )
                }
            },
        )
    }
}

// FreeMind's own keys for folding.
class FoldCommand(
    val label: String,
    val shortcut: String,
    val isAlt: Boolean,
    val isCtrl: Boolean,
    val key: Key,
    val run: (MindMapState) -> Unit,
)

val FOLD_COMMANDS = listOf(
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

// The copied node goes on the system clipboard too, as an outline, to be pasted into other programs.
// The outlines of the selected nodes follow each other, as the nodes of a text pasted as nodes do.
private fun MindMapState.copyNodeToClipboard() {
    val branches = selectedBranches.ifEmpty { return }
    putOnClipboard(branches.joinToString("\n") { nodeOutline(tree, it.id) })
    copyNode()
}

private fun MindMapState.cutNodeToClipboard() {
    val branches = selectedBranches.takeIf { it.isRemovable() } ?: return
    putOnClipboard(branches.joinToString("\n") { nodeOutline(tree, it.id) })
    cutNode()
}

// The root is never removed, and it is the only branch there is when it is selected.
private fun List<NodeData<*>>.isRemovable(): Boolean = any { it.parentId != null }

// The text on the clipboard when a node was copied last. While the clipboard holds it, it is the copied node
// that is pasted, with all of what the outline of it leaves out.
private var copiedNodeClipboardText: String? = null

private fun putOnClipboard(text: String) {
    copiedNodeClipboardText = try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        text
    } catch (e: IllegalStateException) {
        // Another program may have the clipboard for now; the node is still copied to paste in the mind map.
        clipboardText()
    }
}

private fun clipboardText(): String? {
    return try {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (e: IllegalStateException) {
        null
    } catch (e: UnsupportedFlavorException) {
        null
    } catch (e: IOException) {
        null
    }
}

private fun hasClipboardText(): Boolean {
    return try {
        Toolkit.getDefaultToolkit().systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
    } catch (e: IllegalStateException) {
        false
    }
}

/**
 * Pastes the node that was copied last, or the text on the clipboard as nodes when another program put it
 * there since. The nodes of the text look like the last child of the node they are added to.
 */
private fun MindMapState.pasteFromClipboard(preferences: UserPreferences) {
    val parent = selectedNode ?: return
    val text = clipboardText()
    val nodes = if (text == null || text == copiedNodeClipboardText) emptyList() else parseOutline(text)
    if (nodes.isEmpty()) {
        pasteNode()
        return
    }
    val style = newNodeStyle(tree, parent, layoutSide, defaultShape = preferences.newNodeShape)
    addNodes(nodes, style.shape, style.color, style.textColor)
}

private fun chooseFile(
    window: Frame?,
    title: String,
    mode: Int,
    current: File?,
    extensions: List<String> = listOf(MIND_MAP_VIEW_EXTENSION),
): File? {
    val dialog = FileDialog(window, title, mode)
    dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(it, ignoreCase = true) } }
    dialog.directory = current?.parent
    dialog.file = current?.name ?: "*${extensions.first()}"
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
    successMessage: SuccessMessage?,
    onSuccessMessageDismiss: () -> Unit,
    fileActions: FileActions,
    recentPaths: List<String>,
    fileMenuExpanded: Boolean,
    onFileMenuExpandedChange: (Boolean) -> Unit,
    arrowLinkSourceId: String?,
    onArrowLinkSourceIdChange: (String?) -> Unit,
    createdNodeCount: Int,
    onCreatedNodeCountChange: (Int) -> Unit,
    attributesDialogOpen: Boolean,
    onAttributesDialogOpenChange: (Boolean) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
    preferences: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit,
    saveQuestion: String?,
    onSaveChoice: (SaveChoice) -> Unit,
    onFind: () -> Unit,
    findBar: @Composable () -> Unit,
) {
    val selectedNode = state.selectedNode
    var operation by remember { mutableStateOf<Operation?>(null) }
    // The arrow link its menu is open for, and where in the mind map it was asked for.
    var arrowLinkMenu by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    // The node its menu is open for, and where in the mind map it was asked for.
    var nodeMenu by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var isRemoveConfirmOpen by remember { mutableStateOf(false) }
    var isPreferencesOpen by remember { mutableStateOf(false) }
    // A node that is removed without being confirmed is removed at once.
    val removeNode = { if (preferences.isRemoveConfirmed) isRemoveConfirmOpen = true else state.removeNode() }

    // The note of the hovered node and where the pointer was when it was shown, once the pointer has stayed on the node.
    var notePopup by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val hoveredNode = state.hoveredNode
    val isDialogOpen =
        operation != null || attributesDialogOpen || isRemoveConfirmOpen || isPreferencesOpen || saveQuestion != null

    val isMenuOpen = nodeMenu != null || arrowLinkMenu != null
    LaunchedEffect(isDialogOpen, isMenuOpen) { onDialogOpenChange(isDialogOpen || isMenuOpen) }

    LaunchedEffect(successMessage) {
        if (successMessage == null) return@LaunchedEffect
        delay(SUCCESS_MESSAGE_MILLIS)
        onSuccessMessageDismiss()
    }

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
        // The note is only drawn once its images are loaded, as an image that is loaded later does not show.
        UrlImageLoader.preload(note)
        notePopup = note to state.hoverPosition
    }

    val isDarkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {

                    FileMenu(
                        fileActions
                        , recentPaths
                        , fileMenuExpanded
                        , onFileMenuExpandedChange
                    )

                    EditMenu(
                        canUndo = state.canUndo,
                        onUndo = { state.undo() },
                        canRedo = state.canRedo,
                        onRedo = { state.redo() },
                        enabled = selectedNode != null,
                        onAdd = { operation = Operation.Add(it) },
                        onEdit = { operation = Operation.Update },
                        removeEnabled = state.selectedBranches.isRemovable(),
                        onRemove = removeNode,
                        onCut = { state.cutNodeToClipboard() },
                        onCopy = { state.copyNodeToClipboard() },
                        // Read when the menu opens, as canMove is: the clipboard may change in another program.
                        pasteEnabled = { selectedNode != null && (state.hasCopiedNode || hasClipboardText()) },
                        onPaste = { state.pasteFromClipboard(preferences) },
                        onSelectAll = { state.selectAll() },
                        // Read when the menu opens: moving a node does not change the selected node.
                        // The root is in no branch, so it is not moved.
                        canMove = { selectedNode?.parentId != null },
                        onMoveAround = { isClockwise -> state.moveNodeAround(isClockwise) },
                        // Read when the menu opens, as canMove is.
                        links = {
                            selectedNode?.let { RichText.links(it.description) + RichText.links(it.note) }.orEmpty()
                        },
                        onOpenLink = openLink,
                        onAddArrowLink = { onArrowLinkSourceIdChange(selectedNode?.id) },
                        onEditAttributes = { onAttributesDialogOpenChange(true) },
                        onPreferences = { isPreferencesOpen = true },
                        onFind = onFind,
                    )
                    ViewMenu(
                        onFit = { state.fitScreen() },
                        showAttributes = state.showAttributes,
                        onShowAttributesChange = { state.showAttributes = it },
                        onFold = { it.run(state) },
                    )
                }
                findBar()

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
                            operation = Operation.Add(side = side)
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
                    successMessage?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        ) {
                            Text(message.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                                onMoveAround = { isClockwise -> state.moveNodeAround(isClockwise) },
                                // Only a node with children that is not the root is folded.
                                isFolded =
                                    if (node.parentId == null || node.children.isEmpty()) null
                                    else state.tree.isFolded(nodeId),
                                onFold = { state.toggleFold() },
                                onCut = { state.cutNodeToClipboard() },
                                onCopy = { state.copyNodeToClipboard() },
                                isPasteEnabled = state.hasCopiedNode || hasClipboardText(),
                                onPaste = { state.pasteFromClipboard(preferences) },
                                // The root is not removed.
                                isRemovable = state.selectedBranches.isRemovable(),
                                onRemove = removeNode,
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
                                colors = preferences.arrowLinkColors,
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

        saveQuestion?.let { question -> SaveChangesDialog(question, onSaveChoice) }

        if (isRemoveConfirmOpen && state.selectedBranches.isRemovable()) {
            RemoveNodeDialog(
                count = state.selectedBranches.size,
                onConfirm = {
                    isRemoveConfirmOpen = false
                    state.removeNode()
                },
                onDismiss = { isRemoveConfirmOpen = false },
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

        if (isPreferencesOpen) {
            PreferencesDialog(
                preferences = preferences
                , onSubmit = onPreferencesChange
                , onDismiss = { isPreferencesOpen = false }
            )
        }

        operation?.let { currentOperation ->
            val nodeNumber = remember(currentOperation) { nextNodeNumber(state.tree, createdNodeCount) }
            val newNodeStyle = remember(currentOperation) {
                (currentOperation as? Operation.Add)?.let { add ->
                    selectedNode?.let {
                        newNodeStyle(state.tree, it, add.side ?: state.layoutSide, add.shape, preferences.newNodeShape)
                    }
                }
            }
            EditNodeDialog(
                title = if (currentOperation is Operation.Update) "Edit node" else "Add node",
                description =
                    if (currentOperation is Operation.Update) selectedNode?.description.orEmpty()
                    else NEW_NODE_DESCRIPTION + nodeNumber,
                // The description of a new node is only a start, so typing replaces it.
                isDescriptionSelected = currentOperation is Operation.Add,
                note = if (currentOperation is Operation.Update) selectedNode?.note.orEmpty() else "",
                // The root stays the oval it is, so it is given no shape to choose from.
                shape = when (currentOperation) {
                    is Operation.Add -> newNodeStyle?.shape
                    Operation.Update -> selectedNode?.takeIf { it.parentId != null }?.shape
                },
                color = when (currentOperation) {
                    is Operation.Add -> newNodeStyle?.color ?: Color.Unspecified
                    Operation.Update -> selectedNode?.color ?: Color.Unspecified
                },
                textColor = when (currentOperation) {
                    is Operation.Add -> newNodeStyle?.textColor ?: Color.Unspecified
                    Operation.Update -> selectedNode?.textColor ?: Color.Unspecified
                },
                nodeColors = preferences.nodeColors,
                textColors = preferences.textColors,
                onSubmit = { description, note, shape, color, textColor ->
                    when (currentOperation) {
                        is Operation.Add -> {
                            state.addNode(
                                description,
                                shape ?: preferences.newNodeShape,
                                color,
                                textColor,
                                currentOperation.side ?: state.layoutSide,
                                note,
                            )
                            onCreatedNodeCountChange(nodeNumber)
                        }
                        Operation.Update -> state.groupChanges {
                            shape?.let { state.editNodeShape(it) }
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
    onMoveAround: (isClockwise: Boolean) -> Unit,
    isFolded: Boolean?,
    onFold: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    isPasteEnabled: Boolean,
    onPaste: () -> Unit,
    isRemovable: Boolean,
    onRemove: () -> Unit,
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
                // The branch goes to the next of the root's add buttons round from the one it is at.
                listOf(
                    Triple("Move Clockwise", "\u21BB", true)
                    , Triple("Move Counterclockwise", "\u21BA", false)
                ).forEach { (text, icon, isClockwise) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        trailingIcon = { Text(icon) },
                        onClick = {
                            onDismiss()
                            onMoveAround(isClockwise)
                        },
                    )
                }
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
            if (isRemovable) {
                DropdownMenuItem(
                    text = { Text("Cut") },
                    onClick = {
                        onDismiss()
                        onCut()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    onDismiss()
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text("Paste") },
                onClick = {
                    onDismiss()
                    onPaste()
                },
                enabled = isPasteEnabled,
            )
            if (isRemovable) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        onDismiss()
                        onRemove()
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
    colors: List<Color>,
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
                    colors = remember(link.color, colors) { (colors + link.color).distinct() },
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
// The image loader is what shows the images of a note, and the rich text library has it as experimental.
@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun NotePopup(
    note: String
    , position: Offset
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val noteState = remember(note, linkColor) { RichTextState().apply { config.linkColor = linkColor }.setHtml(note) }
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
                RichTextView(noteState, color = MaterialTheme.colorScheme.onSurface, imageLoader = UrlImageLoader)
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

// The link the cursor is in is changed; otherwise the selection becomes a link, or the URL is put in as one.
fun RichTextState.setLink(url: String) {
    when {
        isLink -> updateLink(url)
        !selection.collapsed -> addLinkToSelection(url)
        else -> addLink(text = url, url = url)
    }
}

fun linkScheme(url: String): String? = URL_SCHEME.find(url)?.groupValues?.get(1)?.lowercase()

// An address typed without a scheme is taken to be a mail address when it looks like one, and a web address otherwise.
fun completeUrl(input: String): String {
    val url = input.trim()
    return when {
        url.isEmpty() || linkScheme(url) != null -> url
        '@' in url && '/' !in url -> "mailto:$url"
        else -> "https://$url"
    }
}

fun RichTextState.setSize(size: RichTextSize) {
    // A size is only taken away by the very size the text has, so a selection is first given one size throughout.
    if (!selection.collapsed) addSpanStyle(SpanStyle(fontSize = 1.em))
    removeSpanStyle(SpanStyle(fontSize = currentSpanStyle.fontSize))
    if (size != RichTextSize.NORMAL) addSpanStyle(size.spanStyle)
}
