package com.mindsync.mindmapview.desktop

import com.mindsync.mindmapview.desktop.dialog.SaveChoice

/** The title of the window: the name of the file shown, marked with a * while it has unsaved changes. */
internal fun windowTitle(fileName: String?, isModified: Boolean): String {
    val title = fileName?.let { "$it - $APP_NAME" } ?: APP_NAME
    return if (isModified) "*$title" else title
}

/**
 * The name the menu bar shows for the map in the file [fileName], or for a new map, which has none yet. A map with
 * changes that are not saved is marked with a `*`, as the window's title is.
 */
internal fun mapName(fileName: String?, isModified: Boolean): String {
    val name = fileName ?: "Untitled"
    return if (isModified) "*$name" else name
}

internal fun saveChangesQuestion(fileName: String?): String =
    "Save changes to ${fileName ?: "this mind map"}?"

/**
 * Whether to go on to what the [choice] was asked for. Only [SaveChoice.SAVE] calls [save], and the map may
 * not be saved after all: no file is chosen to save it to, or it cannot be written.
 */
// Inline, so that [save] may be one that waits for a file to be chosen.
internal inline fun goesOn(
    choice: SaveChoice
    , save: () -> Boolean
): Boolean = when (choice) {
    SaveChoice.SAVE -> save()
    SaveChoice.DISCARD -> true
    SaveChoice.CANCEL -> false
}
