package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A file a mind map is opened from, saved to or exported to: on the desktop a file on disk, and in a browser
 * one the user chose.
 */
interface MapDocument {
    val name: String
}

/** A file that could not be read or written, with a [message] that says why. */
class MapFileException(
    message: String?
) : Exception(message)

/** What the app does in its own way on each platform it runs on. */
interface AppPlatform {
    val files: MapFiles
    val clipboard: SystemClipboard
    val preferencesStore: UserPreferencesStore

    /**
     * The mind maps opened last, or null where they cannot be opened again, as in a browser without the File
     * System Access API.
     */
    val recentMaps: RecentMaps?

    /** Returns [image] as the bytes of a PNG file. */
    fun encodePng(image: ImageBitmap): ByteArray
}

interface MapFiles {
    /** Whether Save writes the mind map back to [document], instead of asking where to save it. */
    fun isSavedTo(document: MapDocument): Boolean

    /** Returns the mind map the user chooses to open, or null when they choose none. */
    suspend fun chooseToOpen(current: MapDocument?): MapDocument?

    /**
     * Returns where the user chooses to save the mind map in [current] to, in this app's own format. A mind map
     * that has no file yet suggests [newName].
     */
    suspend fun chooseToSave(
        current: MapDocument?
        , newName: String
    ): MapDocument?

    /** Returns where the user chooses to export the mind map in [current] to, as a file with [extension]. */
    suspend fun chooseToExport(
        current: MapDocument?
        , extension: String
    ): MapDocument?

    /** @throws MapFileException when [document] cannot be read. */
    suspend fun read(document: MapDocument): String

    /** @throws MapFileException when [document] cannot be written. */
    suspend fun write(
        document: MapDocument
        , content: ByteArray
    )
}

/**
 * The mind maps opened last, each known by a path: on the desktop the path of its file, and in a browser a key
 * that ends in the file's name, as a page is not told where a file is.
 */
interface RecentMaps {
    /** The paths of the mind maps, the most recent first. */
    val paths: List<String>

    suspend fun add(document: MapDocument): List<String>

    suspend fun remove(path: String): List<String>

    fun documentAt(path: String): MapDocument

    /** Whether the mind map at [path] can be opened, which a browser may first ask the user to allow. */
    suspend fun access(path: String): RecentMapAccess

    /** Where the mind map at [path] is, as the user is shown it. */
    fun locationOf(path: String): String = path

    /** The folder of the mind map at [path], to tell apart maps of the same name, or null when it is not known. */
    fun folderOf(path: String): String? = com.mindsync.mindmapview.desktop.folderOf(path)
}

enum class RecentMapAccess {
    AVAILABLE
    , MISSING
    , DENIED
}

interface SystemClipboard {
    /** Puts [text] on the clipboard, returning whether it is there: another program may have it for now. */
    suspend fun setText(text: String): Boolean

    suspend fun text(): String?

    /** Whether there may be text to paste. Where that cannot be known beforehand, there may be. */
    fun hasText(): Boolean
}
