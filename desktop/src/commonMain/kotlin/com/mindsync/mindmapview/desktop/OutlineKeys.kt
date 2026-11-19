package com.mindsync.mindmapview.desktop

import androidx.compose.ui.input.key.Key

/** What a key does in the outline while no topic's text is typed in it. */
sealed interface OutlineKeyAction {
    /** Does what the key does on the map, as adding a topic to type in or starting to type. */
    data class Inline(val action: InlineKeyAction) : OutlineKeyAction

    /** Moves the selected topics, as an outliner does. */
    data class Move(val move: OutlineMove) : OutlineKeyAction

    /** Selects the topic of the row above, or below. */
    data class SelectRow(val isUp: Boolean) : OutlineKeyAction

    /** Folds the selected topic, or selects its parent when it is folded already or has nothing to fold. */
    data object FoldOrParent : OutlineKeyAction

    /** Unfolds the selected topic, or selects its first child when it is not folded. */
    data object UnfoldOrChild : OutlineKeyAction
}

/**
 * Returns what a key pressed down does in the outline while no text is typed in it, or null when it does what it
 * does anywhere in the app, as the shortcuts with Ctrl. As in an outliner, Tab and Shift+Tab indent and outdent and
 * the arrow keys go along the rows; Enter, F2, Alt+Enter and typing do what they do on the map. With no topic
 * selected, Enter and Tab add a topic to the root, as on the map, and Down selects the root.
 */
fun outlineKeyAction(
    key: Key
    , codePoint: Int
    , isShift: Boolean
    , isCtrl: Boolean
    , isAlt: Boolean
    , isMeta: Boolean
    , hasSelectedNode: Boolean
): OutlineKeyAction? {
    val isPlain = !isCtrl && !isAlt && !isMeta
    val inline = { inlineKeyAction(key, codePoint, isShift, isCtrl, isAlt, isMeta, false, hasSelectedNode) }
    if (!hasSelectedNode) {
        return when {
            isPlain && !isShift && key == Key.DirectionDown -> OutlineKeyAction.SelectRow(isUp = false)
            else -> inline()?.let { OutlineKeyAction.Inline(it) }
        }
    }
    return when {
        key == Key.Tab && isPlain -> OutlineKeyAction.Move(if (isShift) OutlineMove.OUTDENT else OutlineMove.INDENT)
        isPlain && !isShift && key == Key.DirectionUp -> OutlineKeyAction.SelectRow(isUp = true)
        isPlain && !isShift && key == Key.DirectionDown -> OutlineKeyAction.SelectRow(isUp = false)
        isPlain && !isShift && key == Key.DirectionLeft -> OutlineKeyAction.FoldOrParent
        isPlain && !isShift && key == Key.DirectionRight -> OutlineKeyAction.UnfoldOrChild
        else -> inline()?.let { OutlineKeyAction.Inline(it) }
    }
}
