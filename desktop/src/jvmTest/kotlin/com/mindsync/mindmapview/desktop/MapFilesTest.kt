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
        assertTrue(isSavedTo(File("rocks.ccmm")))
        assertTrue(isSavedTo(File("rocks.CCMM")))
        assertFalse(isSavedTo(File("rocks.mm")))
    }

    @Test
    fun aFreeMindMapIsSavedNextToItInsteadOfOverIt() {
        assertEquals(File("maps", "rocks.ccmm"), mindMapViewFileFor(File("maps", "rocks.mm")))
        assertEquals(File("maps", "rocks.ccmm"), mindMapViewFileFor(File("maps", "rocks.ccmm")))
        assertEquals(File("maps", "rocks.txt.ccmm"), mindMapViewFileFor(File("maps", "rocks.txt")))
    }

    @Test
    fun aNewMapIsNamedAfterItsRootNode() {
        assertEquals("Rocks.ccmm", newMapFileName("<p>Rocks</p>"))
        assertEquals("Igneous rocks.ccmm", newMapFileName("<p><b>Igneous</b> rocks</p>"))
    }

    @Test
    fun aNewMapNameHasNoCharactersAFileNameCannotHave() {
        assertEquals("Rocks and stones.ccmm", newMapFileName("<p>Rocks/and\\stones?</p>"))
        assertEquals("A b.ccmm", newMapFileName("<p>A</p><p>b</p>"))
        assertEquals("Rocks.ccmm", newMapFileName("<p>  ..Rocks.. </p>"))
    }

    @Test
    fun aNewMapNameHasNoEmoji() {
        assertEquals("root.ccmm", newMapFileName("<p>🌱 root</p>"))
        assertEquals("Love it.ccmm", newMapFileName("<p>❤️ Love 👍🏽 it ⭐</p>"))
    }

    @Test
    fun aNewMapWithoutTextInItsRootNodeHasTheDefaultName() {
        assertEquals("mindmap.ccmm", newMapFileName(""))
        assertEquals("mindmap.ccmm", newMapFileName("<p> ?*/ </p>"))
        assertEquals("mindmap.ccmm", newMapFileName("<p>🌱</p>"))
    }

    @Test
    fun aNewMapNameIsKeptShort() {
        assertEquals("a".repeat(100) + ".ccmm", newMapFileName("<p>${"a".repeat(300)}</p>"))
    }

    @Test
    fun aMapIsReadInTheFormatOfItsExtension() {
        val tree = Tree<Any>()
        tree.addNode("a", tree.getRootNode().id, "<p>a</p>", side = LayoutSide.LOWER_LEFT)

        assertEquals(LayoutSide.LOWER_LEFT, readMap("a.ccmm", MindMapViewFile.write(tree)).sideOf("a"))
        assertEquals(LayoutSide.LEFT, readMap("a.MM", FreeMind.write(tree)).sideOf("a"))
    }
}
