package com.mindsync.mindmapview.desktop

import com.mindsync.library.io.MindMapViewFile
import java.io.File

/** Whether the mind map in [file] is saved back to it: only a file in this app's own format is. */
internal fun isSavedTo(file: File): Boolean = file.extension.equals(MindMapViewFile.EXTENSION, ignoreCase = true)

/**
 * Returns the file to save the mind map in [file] to: [file] itself in this app's own format, and otherwise
 * the file of that name in it, so a FreeMind map is not written over.
 */
internal fun mindMapViewFileFor(file: File): File = File(file.parentFile, mindMapViewFileName(file.name))
