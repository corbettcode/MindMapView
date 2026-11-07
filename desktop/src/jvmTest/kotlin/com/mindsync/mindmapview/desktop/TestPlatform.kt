package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import com.mindsync.library.io.OutlineItem

/** A platform for tests of the app, with no files, an empty clipboard and preferences kept only while it runs. */
internal class TestPlatform : AppPlatform {
    override val files: MapFiles = object : MapFiles {
        override fun isSavedTo(document: MapDocument) = false
        override suspend fun chooseToOpen(current: MapDocument?, extensions: List<String>): MapDocument? = null
        override suspend fun chooseToSave(current: MapDocument?, newName: String): MapDocument? = null
        override suspend fun chooseToExport(current: MapDocument?, extension: String): MapDocument? = null
        override suspend fun read(document: MapDocument): String = ""
        override suspend fun readBytes(document: MapDocument): ByteArray = ByteArray(0)
        override suspend fun write(document: MapDocument, content: ByteArray) {}
    }
    override val clipboard: SystemClipboard = object : SystemClipboard {
        override suspend fun setText(text: String) = true
        override suspend fun text(): String? = null
        override fun hasText() = false
    }
    override val preferencesStore = UserPreferencesStore(read = { null }, write = { _, _ -> })
    override val recentMaps: RecentMaps? = null
    override fun encodePng(image: ImageBitmap) = ByteArray(0)
    override val pdfFromSvg: (suspend (svg: String) -> ByteArray)? = null
    override val chooseFolder: (suspend () -> OutlineItem?)? = null
    override suspend fun openUserManual() {}
}
