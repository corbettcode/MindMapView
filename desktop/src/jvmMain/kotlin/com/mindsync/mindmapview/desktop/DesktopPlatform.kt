package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.prefs.Preferences

/** A mind map file on disk. */
internal data class DesktopDocument(
    val file: File
) : MapDocument {
    override val name: String
        get() = file.name
}

private val MapDocument.file: File
    get() = (this as DesktopDocument).file

/** The desktop, where the files chosen are on disk and the window the file dialogs belong to is [window]. */
internal class DesktopPlatform(
    private val window: () -> Frame?
) : AppPlatform {
    override val files: MapFiles = object : MapFiles {
        override fun isSavedTo(document: MapDocument): Boolean = isSavedTo(document.file)

        override suspend fun chooseToOpen(current: MapDocument?): MapDocument? =
            chooseFile("Open", FileDialog.LOAD, current?.file, OPENABLE_EXTENSIONS)?.let(::DesktopDocument)

        override suspend fun chooseToSave(
            current: MapDocument?
            , newName: String
        ): MapDocument? {
            val suggested = current?.file?.let { mindMapViewFileFor(it) } ?: File(newName)
            val chosen = chooseFile("Save As", FileDialog.SAVE, suggested) ?: return null
            return DesktopDocument(mindMapViewFileFor(withExtension(chosen, MIND_MAP_VIEW_EXTENSION)))
        }

        override suspend fun chooseToExport(
            current: MapDocument?
            , extension: String
        ): MapDocument? {
            val suggested = File(current?.file?.parent, exportFileName(current?.name, extension))
            val chosen = chooseFile("Export", FileDialog.SAVE, suggested, listOf(extension)) ?: return null
            return DesktopDocument(withExtension(chosen, extension))
        }

        override suspend fun read(document: MapDocument): String = try {
            document.file.readText()
        } catch (e: IOException) {
            throw MapFileException(e.message)
        }

        override suspend fun write(
            document: MapDocument
            , content: ByteArray
        ) = try {
            document.file.writeBytes(content)
        } catch (e: IOException) {
            throw MapFileException(e.message)
        }
    }

    override val clipboard: SystemClipboard = object : SystemClipboard {
        override suspend fun setText(text: String): Boolean = try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            true
        } catch (e: IllegalStateException) {
            false
        }

        override suspend fun text(): String? = try {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        } catch (e: IllegalStateException) {
            null
        } catch (e: UnsupportedFlavorException) {
            null
        } catch (e: IOException) {
            null
        }

        override fun hasText(): Boolean = try {
            Toolkit.getDefaultToolkit().systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
        } catch (e: IllegalStateException) {
            false
        }
    }

    // The preferences of the user, kept in the preferences of the user that Java keeps.
    override val preferencesStore: UserPreferencesStore by lazy {
        val node = Preferences.userNodeForPackage(UserPreferencesStore::class.java).node("preferences")
        UserPreferencesStore(read = { node.get(it, null) }, write = { name, value -> node.put(name, value) })
    }

    override val recentMaps: RecentMaps = object : RecentMaps {
        private val recentFiles = RecentFiles.ofUser()

        override val paths: List<String>
            get() = recentFiles.paths

        override suspend fun add(document: MapDocument): List<String> = recentFiles.add(document.file)

        override suspend fun remove(path: String): List<String> = recentFiles.remove(File(path))

        override fun documentAt(path: String): MapDocument = DesktopDocument(File(path))

        override suspend fun access(path: String): RecentMapAccess =
            if (File(path).isFile) RecentMapAccess.AVAILABLE else RecentMapAccess.MISSING
    }

    override fun encodePng(image: ImageBitmap): ByteArray = try {
        pngBytes(image)
    } catch (e: IOException) {
        throw MapFileException(e.message)
    }

    // The PDF viewer needs a file of its own, so the manual is copied out of the app's resources. A folder of the
    // app's own keeps its name, which the viewer shows, and keeps it apart from other users' copies.
    private val userManualFile by lazy {
        File(Files.createTempDirectory("mindmapview").toFile(), USER_MANUAL_FILE).apply { deleteOnExit() }
    }

    override suspend fun openUserManual() = withContext(Dispatchers.IO) {
        try {
            val manual = DesktopPlatform::class.java.getResourceAsStream("/$USER_MANUAL_FILE")
                ?: throw MapFileException("it is not in the app")
            manual.use { input -> userManualFile.outputStream().use { input.copyTo(it) } }
            openFile(userManualFile)
        } catch (e: IOException) {
            throw MapFileException(e.message)
        }
    }

    private fun chooseFile(
        title: String
        , mode: Int
        , current: File?
        , extensions: List<String> = listOf(MIND_MAP_VIEW_EXTENSION)
    ): File? {
        val dialog = FileDialog(window(), title, mode)
        dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(it, ignoreCase = true) } }
        dialog.directory = current?.parent
        dialog.file = current?.name ?: "*${extensions.first()}"
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}

// Java opens a file with the desktop's own tools, which some Linux desktops do not have, so xdg-open opens it
// where Java cannot.
private fun openFile(file: File) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        try {
            Desktop.getDesktop().open(file)
            return
        } catch (e: IOException) {
            // xdg-open is tried next.
        }
    }
    ProcessBuilder("xdg-open", file.path).start()
}
