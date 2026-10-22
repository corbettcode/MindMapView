package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.MindMapViewFile

/** The extension of the files mind maps are saved in, which keep all there is of a mind map. */
internal const val MIND_MAP_VIEW_EXTENSION = "." + MindMapViewFile.EXTENSION

/** The extension of FreeMind maps, which are opened as an import and can be exported to. */
internal const val FREE_MIND_EXTENSION = ".mm"

internal val OPENABLE_EXTENSIONS = listOf(MIND_MAP_VIEW_EXTENSION, FREE_MIND_EXTENSION)

internal const val PNG_EXTENSION = ".png"
internal const val HTML_EXTENSION = ".html"

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

/**
 * Returns the name to suggest for an export of the mind map in the file [mapFileName]: the name of the mind map
 * with [extension] in place of its own. A mind map that has no file yet suggests only the extension.
 */
internal fun exportFileName(
    mapFileName: String?
    , extension: String
): String = mapFileName?.let { it.substringBeforeLast('.') + extension } ?: "*$extension"

/**
 * Returns the name to save the mind map named [fileName] under: the name itself in this app's own format, and
 * otherwise that name in it, so a FreeMind map is not written over.
 */
internal fun mindMapViewFileName(
    fileName: String
): String = when {
    fileName.endsWith(MIND_MAP_VIEW_EXTENSION, ignoreCase = true) -> fileName
    fileName.endsWith(FREE_MIND_EXTENSION, ignoreCase = true) ->
        fileName.dropLast(FREE_MIND_EXTENSION.length) + MIND_MAP_VIEW_EXTENSION
    else -> fileName + MIND_MAP_VIEW_EXTENSION
}

/** The name of the file at [path], on any platform's paths. */
internal fun fileNameOf(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

/** The folder of the file at [path], or an empty one when the path has none. */
internal fun folderOf(
    path: String
): String = path
    .dropLast(fileNameOf(path).length)
    .dropLastWhile { it == '/' || it == '\\' }

/** Returns the file names that more than one of the paths ends in. */
internal fun sharedNames(
    paths: List<String>
): Set<String> = paths
    .groupingBy { fileNameOf(it) }
    .eachCount()
    .filterValues { it > 1 }
    .keys
