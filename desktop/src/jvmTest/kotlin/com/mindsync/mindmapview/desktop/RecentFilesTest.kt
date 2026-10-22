package com.mindsync.mindmapview.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentFilesTest {
    private var stored = listOf<String>()

    private fun recentFiles() = RecentFiles(read = { stored }, write = { stored = it })

    private fun path(name: String) = File(name).absolutePath

    @Test
    fun mostRecentFileIsFirst() {
        val recentFiles = recentFiles()
        recentFiles.add(File("a.mm"))
        assertEquals(listOf(path("b.mm"), path("a.mm")), recentFiles.add(File("b.mm")))
        assertEquals(listOf(path("b.mm"), path("a.mm")), recentFiles.paths)
    }

    @Test
    fun fileThatIsOpenedAgainMovesToTheFront() {
        val recentFiles = recentFiles()
        listOf("a.mm", "b.mm", "c.mm", "a.mm").forEach { recentFiles.add(File(it)) }
        assertEquals(listOf(path("a.mm"), path("c.mm"), path("b.mm")), recentFiles.paths)
    }

    @Test
    fun onlyTheLastTenFilesAreKept() {
        val recentFiles = recentFiles()
        (1..12).forEach { recentFiles.add(File("$it.mm")) }
        assertEquals((12 downTo 3).map { path("$it.mm") }, recentFiles.paths)
    }

    @Test
    fun fileIsRemoved() {
        val recentFiles = recentFiles()
        listOf("a.mm", "b.mm").forEach { recentFiles.add(File(it)) }
        assertEquals(listOf(path("b.mm")), recentFiles.remove(File("a.mm")))
        assertEquals(listOf(path("b.mm")), recentFiles.remove(File("not there.mm")))
    }

    @Test
    fun filesAreKeptForTheNextRun() {
        listOf("a.mm", "b.mm").forEach { recentFiles().add(File(it)) }
        assertEquals(listOf(path("b.mm"), path("a.mm")), recentFiles().paths)

        stored = (1..12).map { path("$it.mm") } + path("1.mm")
        assertEquals((1..10).map { path("$it.mm") }, recentFiles().paths)
    }

    @Test
    fun sharedNamesAreTheNamesOfFilesInMoreThanOneFolder() {
        val paths = listOf("/one/a.mm", "/two/a.mm", "/one/b.mm", "/two/c.mm")
        assertEquals(setOf("a.mm"), sharedNames(paths))
    }
}
