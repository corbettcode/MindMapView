package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportFilesTest {
    @Test
    fun exportIsNamedAfterTheMindMap() {
        assertEquals("rocks.png", exportFileName("rocks.mm", PNG_EXTENSION))
        assertEquals("rocks.html", exportFileName("rocks", HTML_EXTENSION))
    }

    @Test
    fun exportOfAMindMapWithoutAFileHasOnlyItsExtension() {
        assertEquals("*.png", exportFileName(null, PNG_EXTENSION))
    }

    @Test
    fun extensionIsAddedToAFileWithoutOne() {
        assertEquals(File("/maps/rocks.png"), withExtension(File("/maps/rocks"), PNG_EXTENSION))
    }

    @Test
    fun fileWithAnExtensionKeepsIt() {
        assertEquals(File("/maps/rocks.PNG"), withExtension(File("/maps/rocks.PNG"), PNG_EXTENSION))
    }

    @Test
    fun imageIsWrittenAsAPng() {
        val file = Files.createTempFile("export", PNG_EXTENSION).toFile()
        try {
            writePng(ImageBitmap(30, 20), file)

            val written = ImageIO.read(file)
            assertEquals(30, written.width)
            assertEquals(20, written.height)
        } finally {
            file.delete()
        }
    }
}
