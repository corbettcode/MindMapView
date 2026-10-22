package com.mindsync.mindmapview.desktop

import com.mindsync.mindmapview.desktop.dialog.SaveChoice

/** The title of the window: the name of the file shown, marked with a * while it has unsaved changes. */
internal fun windowTitle(fileName: String?, isModified: Boolean): String {
    val title = fileName?.let { "$it - $APP_NAME" } ?: APP_NAME
    return if (isModified) "*$title" else title
}

internal fun saveChangesQuestion(fileName: String?): String =
    "Save changes to ${fileName ?: "this mind map"}?"

/**
 * Whether to go on to what the [choice] was asked for. Only [SaveChoice.SAVE] calls [save], and the map may
 * not be saved after all: no file is chosen to save it to, or it cannot be written.
 */
internal fun goesOn(
    choice: SaveChoice
    , save: () -> Boolean
): Boolean = when (choice) {
    SaveChoice.SAVE -> save()
    SaveChoice.DISCARD -> true
    SaveChoice.CANCEL -> false
}
