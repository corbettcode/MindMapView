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

    /** The mind maps opened last, or null where they cannot be opened again, as in a browser. */
    val recentMaps: RecentMaps?

    /** Returns [image] as the bytes of a PNG file. */
    fun encodePng(image: ImageBitmap): ByteArray
}

interface MapFiles {
    /** Whether Save writes the mind map back to [document], instead of asking where to save it. */
    fun isSavedTo(document: MapDocument): Boolean

    /** Returns the mind map the user chooses to open, or null when they choose none. */
    suspend fun chooseToOpen(current: MapDocument?): MapDocument?

    /** Returns where the user chooses to save the mind map in [current] to, in this app's own format. */
    suspend fun chooseToSave(current: MapDocument?): MapDocument?

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

interface RecentMaps {
    /** The paths of the mind maps, the most recent first. */
    val paths: List<String>

    fun add(document: MapDocument): List<String>

    fun remove(document: MapDocument): List<String>

    fun documentAt(path: String): MapDocument
}

interface SystemClipboard {
    /** Puts [text] on the clipboard, returning whether it is there: another program may have it for now. */
    suspend fun setText(text: String): Boolean

    suspend fun text(): String?

    /** Whether there may be text to paste. Where that cannot be known beforehand, there may be. */
    fun hasText(): Boolean
}
