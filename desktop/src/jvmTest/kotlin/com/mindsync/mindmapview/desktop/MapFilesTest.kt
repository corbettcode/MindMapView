package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.LayoutSide
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapFilesTest {
    @Test
    fun onlyAMindMapViewFileIsSavedBackTo() {
        assertTrue(isSavedTo(File("rocks.mmv")))
        assertTrue(isSavedTo(File("rocks.MMV")))
        assertFalse(isSavedTo(File("rocks.mm")))
    }

    @Test
    fun aFreeMindMapIsSavedNextToItInsteadOfOverIt() {
        assertEquals(File("maps", "rocks.mmv"), mindMapViewFileFor(File("maps", "rocks.mm")))
        assertEquals(File("maps", "rocks.mmv"), mindMapViewFileFor(File("maps", "rocks.mmv")))
        assertEquals(File("maps", "rocks.txt.mmv"), mindMapViewFileFor(File("maps", "rocks.txt")))
    }

    @Test
    fun aMapIsReadInTheFormatOfItsExtension() {
        val tree = Tree<Any>()
        tree.addNode("a", tree.getRootNode().id, "<p>a</p>", side = LayoutSide.LOWER_LEFT)

        assertEquals(LayoutSide.LOWER_LEFT, readMap("a.mmv", MindMapViewFile.write(tree)).sideOf("a"))
        assertEquals(LayoutSide.LEFT, readMap("a.MM", FreeMind.write(tree)).sideOf("a"))
    }
}
