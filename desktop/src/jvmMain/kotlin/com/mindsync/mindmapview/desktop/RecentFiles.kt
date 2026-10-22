package com.mindsync.mindmapview.desktop

import java.io.File
import java.util.prefs.Preferences

/**
 * The mind maps that were opened last, the most recent first. They are read with [read] to start with and
 * written with [write] on every change, so they are kept from one run of the app to the next.
 */
internal class RecentFiles(
    read: () -> List<String>,
    private val write: (List<String>) -> Unit,
) {
    var paths: List<String> = read().distinct().take(MAX_COUNT)
        private set

    /** Puts the file first, where it moves to when it is one of the recent files already. */
    fun add(file: File): List<String> = update(listOf(file.absolutePath) + paths)

    fun remove(file: File): List<String> = update(paths - file.absolutePath)

    private fun update(newPaths: List<String>): List<String> {
        paths = newPaths.distinct().take(MAX_COUNT)
        write(paths)
        return paths
    }

    companion object {
        const val MAX_COUNT = 10

        /** The recent files of the user, kept in the preferences of the user. */
        fun ofUser(): RecentFiles {
            val preferences = Preferences.userNodeForPackage(RecentFiles::class.java).node("recentFiles")
            // A path is kept under its place among the recent files, as a value is too short to hold all of them.
            return RecentFiles(
                read = { (0 until MAX_COUNT).mapNotNull { preferences.get(it.toString(), null) } },
                write = { paths ->
                    preferences.clear()
                    paths.forEachIndexed { index, path -> preferences.put(index.toString(), path) }
                },
            )
        }
    }
}
