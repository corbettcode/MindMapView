package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.MindMapViewFile
import com.mohamedrejeb.richeditor.model.RichTextState

/** The extension of the files mind maps are saved in, which keep all there is of a mind map. */
internal const val MIND_MAP_VIEW_EXTENSION = "." + MindMapViewFile.EXTENSION

/** The extension of FreeMind maps, which are opened as an import and can be exported to. */
internal const val FREE_MIND_EXTENSION = ".mm"

internal val OPENABLE_EXTENSIONS = listOf(MIND_MAP_VIEW_EXTENSION, FREE_MIND_EXTENSION)

/** The name of a mind map that has no file yet, and whose root node has no text to name it after. */
internal const val NEW_MAP_NAME = "mindmap"

private const val MAX_NEW_MAP_NAME_LENGTH = 100

// Characters that a file name may not have on one system or another.
private val UNSAFE_FILE_NAME_CHARACTERS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
private val WHITESPACE = Regex("""\s+""")

// The parts emoji are made of: a file dialog may not show them, and shows other characters in their place.
private const val EMOJI_VARIATION = '\uFE0F'
private const val ZERO_WIDTH_JOINER = '\u200D'
private const val KEYCAP = '\u20E3'

private fun Char.isEmojiPart(): Boolean =
    isSurrogate() || category == CharCategory.OTHER_SYMBOL || this == EMOJI_VARIATION || this == ZERO_WIDTH_JOINER
        || this == KEYCAP

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

/**
 * Returns the name to suggest for a mind map that has no file yet: the text of its root node, whose description is
 * [rootDescription], made fit to be a file name. Emoji, such as the one a root node starts with, are dropped.
 * Leading and trailing dots are dropped too, as a name that starts with one is hidden, and one that ends with one
 * is not kept on Windows.
 */
internal fun newMapFileName(
    rootDescription: String
): String {
    val text = RichTextState()
        .setHtml(rootDescription)
        .annotatedString
        .text
    val name = text
        .filterNot { it.isEmojiPart() }
        .replace(UNSAFE_FILE_NAME_CHARACTERS, " ")
        .replace(WHITESPACE, " ")
        .trim(' ', '.')
        .take(MAX_NEW_MAP_NAME_LENGTH)
        .trimEnd(' ', '.')
    return name.ifEmpty { NEW_MAP_NAME } + MIND_MAP_VIEW_EXTENSION
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
