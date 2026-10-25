package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
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

        override fun add(document: MapDocument): List<String> = recentFiles.add(document.file)

        override fun remove(document: MapDocument): List<String> = recentFiles.remove(document.file)

        override fun documentAt(path: String): MapDocument = DesktopDocument(File(path))

        override fun isMissing(path: String): Boolean = !File(path).isFile
    }

    override fun encodePng(image: ImageBitmap): ByteArray = try {
        pngBytes(image)
    } catch (e: IOException) {
        throw MapFileException(e.message)
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
