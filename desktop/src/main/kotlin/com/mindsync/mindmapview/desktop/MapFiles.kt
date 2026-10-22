package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.MindMapViewFile
import java.io.File

/** The extension of the files mind maps are saved in, which keep all there is of a mind map. */
internal const val MIND_MAP_VIEW_EXTENSION = "." + MindMapViewFile.EXTENSION

/** The extension of FreeMind maps, which are opened as an import and can be exported to. */
internal const val FREE_MIND_EXTENSION = ".mm"

internal val OPENABLE_EXTENSIONS = listOf(MIND_MAP_VIEW_EXTENSION, FREE_MIND_EXTENSION)

/** Whether the mind map in [file] is saved back to it: only a file in this app's own format is. */
internal fun isSavedTo(file: File): Boolean = file.extension.equals(MindMapViewFile.EXTENSION, ignoreCase = true)

/**
 * Returns the file to save the mind map in [file] to: [file] itself in this app's own format, and otherwise
 * the file of that name in it, so a FreeMind map is not written over.
 */
internal fun mindMapViewFileFor(file: File): File = when {
    isSavedTo(file) -> file
    file.extension.equals(FREE_MIND_EXTENSION.removePrefix("."), ignoreCase = true) ->
        File(file.parentFile, file.nameWithoutExtension + MIND_MAP_VIEW_EXTENSION)
    else -> File(file.path + MIND_MAP_VIEW_EXTENSION)
}

/**
 * Reads the mind map [content] of a file named [fileName]: a FreeMind map when its extension is FreeMind's,
 * and otherwise one in this app's own format.
 *
 * @throws IllegalArgumentException when [content] is not a mind map in that format.
 */
internal fun readMap(
    fileName: String
    , content: String
): Tree<Any> {
    val isFreeMind = fileName.endsWith(FREE_MIND_EXTENSION, ignoreCase = true)
    return if (isFreeMind) FreeMind.read(content) else MindMapViewFile.read(content)
}
