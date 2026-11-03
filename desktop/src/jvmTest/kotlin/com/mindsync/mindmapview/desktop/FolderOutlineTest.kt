package com.mindsync.mindmapview.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderOutlineTest {
    private val folder: File = Files.createTempDirectory("folder-outline").toFile()

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    private fun folders(vararg paths: String) = paths.forEach { File(folder, it).mkdirs() }

    @Test
    fun theFoldersAreItsChildrenByName() {
        folders("b/inner", "A", "c")
        File(folder, "file.txt").writeText("not a folder")

        val outline = folderOutline(folder)

        assertEquals(folder.name, outline.title)
        assertEquals(listOf("A", "b", "c"), outline.children.map { it.title })
        assertEquals(listOf("inner"), outline.children[1].children.map { it.title })
        assertTrue(folder.absolutePath in outline.note)
    }

    @Test
    fun hiddenFoldersAndLinksAreLeftOut() {
        folders(".git", "shown", "target")
        Files.createSymbolicLink(File(folder, "link").toPath(), File(folder, "target").toPath())

        assertEquals(listOf("shown", "target"), folderOutline(folder).children.map { it.title })
    }

    @Test
    fun foldersBelowTheChildrenStartFolded() {
        folders("a/b/c")

        val a = folderOutline(folder).children.single()

        assertFalse(a.isFolded)
        assertTrue(a.children.single().isFolded)
    }

    @Test
    fun theDeepestFoldersAreLeftOutPastTheMost() {
        folders("a/deep", "b", "c")

        val outline = folderOutline(folder, maxFolders = 3)

        assertEquals(listOf("a", "b", "c"), outline.children.map { it.title })
        assertEquals(emptyList(), outline.children[0].children)
        assertTrue("Only the first 3 folders" in outline.note)
        assertFalse("Only the first" in folderOutline(folder).note)
    }

    @Test
    fun aFolderOpenedWithADoubleClickIsTheOneChosen() {
        folders("projects")
        val projects = File(folder, "projects")

        assertEquals(projects, chosenFolder(File(projects, "projects"), projects))
        assertEquals(projects, chosenFolder(projects, folder))
        assertFailsWith<MapFileException> { chosenFolder(File(folder, "missing"), folder) }
    }

    @Test
    fun aFolderWithoutFoldersIsNoMap() {
        File(folder, "file.txt").writeText("not a folder")

        assertFailsWith<MapFileException> { folderOutline(folder) }
        assertFailsWith<MapFileException> { folderOutline(File(folder, "missing")) }
    }
}
