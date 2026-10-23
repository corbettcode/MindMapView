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
    fun aNewMapIsNamedAfterItsRootNode() {
        assertEquals("Rocks.mmv", newMapFileName("<p>Rocks</p>"))
        assertEquals("Igneous rocks.mmv", newMapFileName("<p><b>Igneous</b> rocks</p>"))
    }

    @Test
    fun aNewMapNameHasNoCharactersAFileNameCannotHave() {
        assertEquals("Rocks and stones.mmv", newMapFileName("<p>Rocks/and\\stones?</p>"))
        assertEquals("A b.mmv", newMapFileName("<p>A</p><p>b</p>"))
        assertEquals("Rocks.mmv", newMapFileName("<p>  ..Rocks.. </p>"))
    }

    @Test
    fun aNewMapNameHasNoEmoji() {
        assertEquals("root.mmv", newMapFileName("<p>🌱 root</p>"))
        assertEquals("Love it.mmv", newMapFileName("<p>❤️ Love 👍🏽 it ⭐</p>"))
    }

    @Test
    fun aNewMapWithoutTextInItsRootNodeHasTheDefaultName() {
        assertEquals("mindmap.mmv", newMapFileName(""))
        assertEquals("mindmap.mmv", newMapFileName("<p> ?*/ </p>"))
        assertEquals("mindmap.mmv", newMapFileName("<p>🌱</p>"))
    }

    @Test
    fun aNewMapNameIsKeptShort() {
        assertEquals("a".repeat(100) + ".mmv", newMapFileName("<p>${"a".repeat(300)}</p>"))
    }

    @Test
    fun aMapIsReadInTheFormatOfItsExtension() {
        val tree = Tree<Any>()
        tree.addNode("a", tree.getRootNode().id, "<p>a</p>", side = LayoutSide.LOWER_LEFT)

        assertEquals(LayoutSide.LOWER_LEFT, readMap("a.mmv", MindMapViewFile.write(tree)).sideOf("a"))
        assertEquals(LayoutSide.LEFT, readMap("a.MM", FreeMind.write(tree)).sideOf("a"))
    }
}
