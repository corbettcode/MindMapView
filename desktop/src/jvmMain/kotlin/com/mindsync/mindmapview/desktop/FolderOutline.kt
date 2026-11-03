package com.mindsync.mindmapview.desktop

import com.mindsync.library.io.OutlineItem
import com.mindsync.library.util.RichText
import java.io.File
import java.nio.file.Files

/** The most folders a map of a folder has, so that a map of a whole disk stays one that can be shown. */
internal const val MAX_FOLDERS = 1_000

// The folders this deep below the chosen one start folded, so the map opens small.
private const val FOLDED_DEPTH = 2

/**
 * Returns the folder the user chose: [selected], or [current], the folder the chooser shows, when [selected] is
 * that folder's name inside itself. Swing's chooser keeps the name of a folder that was opened with a double
 * click, so choosing then makes a path to a folder of the same name inside it, which is not there.
 *
 * @throws MapFileException when the folder chosen is not there.
 */
internal fun chosenFolder(
    selected: File
    , current: File
): File = when {
    selected.isDirectory -> selected
    selected.name == current.name && current.isDirectory -> current
    else -> throw MapFileException("${selected.path} is not a folder")
}

/**
 * Returns [folder] with the folders in it, by name, and the folders in those in turn. Hidden folders and links to
 * folders are left out, a link as it may lead back up to a folder it is in. Folders are taken a level at a time,
 * so when there are more than [maxFolders] it is the deepest ones that are left out, and the note of the
 * chosen folder says so. Below the chosen folder's children, a folder with folders in it is folded.
 *
 * @throws MapFileException when [folder] cannot be read or has no folders in it, as its map would be its root only.
 */
internal fun folderOutline(
    folder: File
    , maxFolders: Int = MAX_FOLDERS
): OutlineItem {
    if (!folder.isDirectory || folder.listFiles() == null) {
        throw MapFileException("${folder.path} cannot be read")
    }
    val subfolders = mutableMapOf<File, List<File>>()
    val queue = ArrayDeque(listOf(folder))
    var count = 0
    var isComplete = true
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val found = subfoldersOf(current)
        val kept = found.take(maxFolders - count)
        if (kept.size < found.size) isComplete = false
        count += kept.size
        subfolders[current] = kept
        queue.addAll(kept)
    }

    if (subfolders[folder].isNullOrEmpty()) throw MapFileException("${folder.path} has no folders in it")

    fun item(
        current: File
        , depth: Int
    ): OutlineItem {
        val children = subfolders[current].orEmpty().map { item(it, depth + 1) }
        return OutlineItem(
            title = current.name.ifEmpty { current.path }
            , children = children
            , isFolded = depth >= FOLDED_DEPTH
        )
    }

    val note = buildString {
        append("<p>").append(RichText.fromPlainText(folder.absolutePath)).append("</p>")
        if (!isComplete) append("<p>Only the first $maxFolders folders are in the map.</p>")
    }
    return item(folder, depth = 0).copy(note = note)
}

// A folder that cannot be read has no folders to show.
private fun subfoldersOf(folder: File): List<File> {
    return folder
        .listFiles { file -> file.isDirectory && !file.isHidden && !file.name.startsWith(".") }
        .orEmpty()
        .filterNot { Files.isSymbolicLink(it.toPath()) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
