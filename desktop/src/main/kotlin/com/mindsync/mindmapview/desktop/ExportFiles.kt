package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

internal const val PNG_EXTENSION = ".png"
internal const val HTML_EXTENSION = ".html"

/**
 * Returns the name to suggest for an export of the mind map in the file [mapFileName]: the name of the mind map
 * with [extension] in place of its own. A mind map that has no file yet suggests only the extension.
 */
internal fun exportFileName(mapFileName: String?, extension: String): String =
    mapFileName?.let { it.substringBeforeLast('.') + extension } ?: "*$extension"

/** Returns [file], with [extension] added when it has none, as a name that is typed may have. */
internal fun withExtension(file: File, extension: String): File =
    if (file.extension.isEmpty()) File(file.path + extension) else file

/** @throws IOException when the image cannot be written to [file]. */
internal fun writePng(image: ImageBitmap, file: File) {
    if (!ImageIO.write(image.toAwtImage(), "png", file)) throw IOException("No PNG writer is available")
}
