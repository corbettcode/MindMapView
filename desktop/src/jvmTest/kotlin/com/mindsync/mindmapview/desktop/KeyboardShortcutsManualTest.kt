package com.mindsync.mindmapview.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Keyboard Shortcuts dialog and the user manual list the shortcuts each, so a shortcut added to one and not the
 * other is caught here. The keys of each group are compared, not their wording, which may differ.
 */
class KeyboardShortcutsManualTest {
    // The tests run in the desktop module, below the folder the manual is in.
    private val manual: String = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "docs/user-manual/user-manual.html") }
        .first { it.exists() }
        .readText()

    // The keys of each group of shortcuts in the manual, by the title of the group. The mouse is no keyboard.
    private fun manualShortcuts(): Map<String, Set<String>> {
        val section = manual.substringAfter("<section id=\"shortcuts\">").substringBefore("</section>")
        return section.split("<h3>").drop(1)
            .associate { group ->
                val title = group.substringBefore("</h3>").trim()
                val keys = Regex("<tr><td>(.*?)</td><td>").findAll(group).flatMap { row -> keysIn(row.groupValues[1]) }
                title to keys.toSet()
            }
            .filterKeys { it != "Mouse" }
    }

    // The keys of a cell, each in a <kbd>, or the cell itself, as "Arrow keys" is written.
    private fun keysIn(cell: String): List<String> {
        val keys = Regex("<kbd>(.*?)</kbd>").findAll(cell).map { it.groupValues[1] }.toList()
        return keys.ifEmpty { listOf(cell.trim()) }.map { it.replace("&amp;", "&") }
    }

    @Test
    fun dialogListsTheShortcutsTheManualDoes() {
        val dialog = KEYBOARD_SHORTCUTS.associate { group -> group.title to group.shortcuts.flatMap { it.keys }.toSet() }

        assertEquals(manualShortcuts(), dialog)
    }
}
