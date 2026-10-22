package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/** Returns [file], with [extension] added when it has none, as a name that is typed may have. */
internal fun withExtension(file: File, extension: String): File =
    if (file.extension.isEmpty()) File(file.path + extension) else file

/** @throws IOException when the image cannot be written to [file]. */
internal fun writePng(image: ImageBitmap, file: File) {
    file.writeBytes(pngBytes(image))
}

/** @throws IOException when no PNG writer is available. */
internal fun pngBytes(image: ImageBitmap): ByteArray {
    val bytes = ByteArrayOutputStream()
    if (!ImageIO.write(image.toAwtImage(), "png", bytes)) throw IOException("No PNG writer is available")
    return bytes.toByteArray()
}
