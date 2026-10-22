package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLConnection

private const val TIMEOUT_MILLIS = 10_000
private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
private const val USER_AGENT = "MindMapView/1.0 (https://github.com/hegleB/MindMapView)"

internal actual suspend fun fetchImage(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = imageConnection(url) ?: return@withContext null
        val bytes = connection.getInputStream().use { it.readNBytes(MAX_IMAGE_BYTES) }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: IOException) {
        System.err.println("Could not load the image at $url: $e")
        null
    } catch (e: IllegalArgumentException) {
        System.err.println("Could not load the image at $url: $e")
        null
    }
}

/**
 * Returns the connection an image at [url] is fetched over, or null when it is not a web address. Wikimedia,
 * where many images are, refuses a request that does not say what makes it.
 */
internal fun imageConnection(url: String): URLConnection? {
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        return null
    }
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    return uri.toURL().openConnection().apply {
        connectTimeout = TIMEOUT_MILLIS
        readTimeout = TIMEOUT_MILLIS
        setRequestProperty("User-Agent", USER_AGENT)
    }
}
