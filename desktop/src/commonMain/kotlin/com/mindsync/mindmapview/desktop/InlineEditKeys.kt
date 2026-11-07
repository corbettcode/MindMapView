package com.mindsync.mindmapview.desktop

import androidx.compose.ui.input.key.Key

/** Where a node added to be typed into goes, from the selected node. */
enum class NewNodePlace {
    CHILD
    , SIBLING_BELOW
    , SIBLING_ABOVE
}

/** The menu items that add a node to type into, with their keys, in the order they are listed. */
val NEW_NODE_ITEMS = listOf(
    Triple("Add Child", "Tab", NewNodePlace.CHILD)
    , Triple("Add Sibling", "Enter", NewNodePlace.SIBLING_BELOW)
    , Triple("Add Sibling Above", "Shift+Enter", NewNodePlace.SIBLING_ABOVE)
)

/** What a key does for typing the text of a node on the map, as in XMind. */
sealed interface InlineKeyAction {
    /** Adds a node at [place] and starts typing its text. */
    data class AddNode(val place: NewNodePlace) : InlineKeyAction

    /** Starts typing the text of the selected node as it is. */
    data object EditText : InlineKeyAction

    /** Starts typing the text of the selected node, replacing it with [text]. */
    data class TypeText(val text: String) : InlineKeyAction

    /** Opens the node's edit dialog, for all there is of the node. */
    data object OpenEditDialog : InlineKeyAction

    /** Ends the typing. */
    data object Commit : InlineKeyAction

    /** Ends the typing, then adds a child of the node and starts typing its text. */
    data object CommitAndAddChild : InlineKeyAction

    /** Ends the typing without changing the map. */
    data object Cancel : InlineKeyAction

    /** Ends the typing, then lets the key do what it does: save, open, find, the File menu... */
    data object CommitAndGoOn : InlineKeyAction

    /** Does nothing, so the key does nothing else either, as a Tab that would take the focus off the map. */
    data object Ignore : InlineKeyAction
}

/**
 * Returns what a key pressed down does for typing the text of a node, or null when it does something else, or
 * nothing. While a node's text [isEditing], every key that is not an action goes to the text field. Otherwise
 * [hasSelectedNode] tells whether there is a node for the key to act on. With none, Tab and Enter add a child of the
 * root, as the root is the node a map starts from.
 *
 * Tab would move the focus from the map to the menus, so on the map it is always taken, whatever else is pressed with
 * it.
 */
fun inlineKeyAction(
    key: Key
    , codePoint: Int
    , isShift: Boolean
    , isCtrl: Boolean
    , isAlt: Boolean
    , isMeta: Boolean
    , isEditing: Boolean
    , hasSelectedNode: Boolean
): InlineKeyAction? {
    val isEnter = key == Key.Enter || key == Key.NumPadEnter
    val isPlain = !isCtrl && !isAlt && !isMeta
    if (isEditing) {
        return when {
            isEnter && isPlain && !isShift -> InlineKeyAction.Commit
            key == Key.Tab && isPlain && !isShift -> InlineKeyAction.CommitAndAddChild
            key == Key.Tab -> InlineKeyAction.Ignore
            key == Key.Escape -> InlineKeyAction.Cancel
            isCtrl && key in GOING_ON_CTRL_KEYS || isAlt && key == Key.F -> InlineKeyAction.CommitAndGoOn
            else -> null
        }
    }
    return when {
        key == Key.Tab && isPlain && !isShift -> InlineKeyAction.AddNode(NewNodePlace.CHILD)
        key == Key.Tab -> InlineKeyAction.Ignore
        !hasSelectedNode -> if (isEnter && isPlain && !isShift) InlineKeyAction.AddNode(NewNodePlace.CHILD) else null
        isEnter && isPlain -> InlineKeyAction.AddNode(
            if (isShift) NewNodePlace.SIBLING_ABOVE else NewNodePlace.SIBLING_BELOW
        )
        // Freeplane's key for editing a node in a dialog.
        isEnter && isAlt && !isCtrl && !isMeta && !isShift -> InlineKeyAction.OpenEditDialog
        key == Key.F2 && isPlain && !isShift -> InlineKeyAction.EditText
        isPlain -> typedText(codePoint)?.let { InlineKeyAction.TypeText(it) }
        else -> null
    }
}

// Ctrl with these keys does something with the map as a whole, which takes the text as it is typed so far.
private val GOING_ON_CTRL_KEYS = setOf(Key.S, Key.O, Key.N, Key.F, Key.Comma)

/**
 * Returns the character a key types, as the start of a node's text, or null for one that types none. Space folds
 * and '+' adds a child through the dialog, so neither starts typing.
 */
fun typedText(codePoint: Int): String? {
    if (codePoint <= ' '.code || codePoint == '+'.code || codePoint == DELETE) return null
    // Keys that type nothing come with a character of no meaning: AWT's undefined one, or the private ones
    // macOS gives its function and arrow keys.
    if (codePoint == UNDEFINED_CHARACTER || codePoint in PRIVATE_USE || codePoint in SURROGATES) return null
    if (codePoint in C1_CONTROLS || codePoint > MAX_CODE_POINT) return null
    if (codePoint <= UNDEFINED_CHARACTER) return Char(codePoint).toString()
    val offset = codePoint - SUPPLEMENTARY_START
    return charArrayOf(
        Char(SURROGATES.first + (offset shr 10))
        , Char(LOW_SURROGATE_START + (offset and 0x3FF))
    ).concatToString()
}

private const val DELETE = 0x7F
private const val UNDEFINED_CHARACTER = 0xFFFF
private val C1_CONTROLS = 0x80..0x9F
private val PRIVATE_USE = 0xE000..0xF8FF
private val SURROGATES = 0xD800..0xDFFF
private const val LOW_SURROGATE_START = 0xDC00
private const val SUPPLEMENTARY_START = 0x10000
private const val MAX_CODE_POINT = 0x10FFFF
