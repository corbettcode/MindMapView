package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.Image
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap

private const val TIMEOUT_MILLIS = 10_000
private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
private const val PRELOAD_TIMEOUT_MILLIS = 5_000L
private val IMAGE_SOURCE = Regex("""<img\s[^>]*?src="([^"]*)"""", RegexOption.IGNORE_CASE)
private const val USER_AGENT = "MindMapView/1.0 (https://github.com/hegleB/MindMapView)"

/**
 * Loads the images of rich text from their web addresses. Rich text only makes room for an image that is
 * loaded when it is laid out, so the images of a note are loaded with [preload] before it is shown. An image
 * that cannot be loaded shows nothing: the address may not be an image.
 */
internal object UrlImageLoader : ImageLoader {
    // Each image is fetched once, not every time a note that shows it is shown again, and so is a failure.
    private val images = ConcurrentHashMap<String, ImageBitmap>()
    private val failedUrls = ConcurrentHashMap.newKeySet<String>()

    /** Loads the images in [html] that are not loaded yet, giving up on the rest after a while. */
    suspend fun preload(html: String) {
        withTimeoutOrNull(PRELOAD_TIMEOUT_MILLIS) {
            imageSources(html).forEach { url -> withContext(Dispatchers.IO) { imageOf(url) } }
        }
    }

    @Composable
    override fun load(model: Any): ImageData? {
        val url = model.toString()
        val image by produceState(images[url], url) {
            if (value == null) value = withContext(Dispatchers.IO) { imageOf(url) }
        }
        return image?.let { ImageData(painter = BitmapPainter(it)) }
    }

    private fun imageOf(url: String): ImageBitmap? {
        images[url]?.let { return it }
        if (url in failedUrls) return null
        return fetch(url)?.also { images[url] = it } ?: null.also { failedUrls.add(url) }
    }

    private fun fetch(url: String): ImageBitmap? {
        return try {
            val connection = imageConnection(url) ?: return null
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
}

/** Returns the addresses of the images in [html], in the order they are in it. */
internal fun imageSources(html: String): List<String> {
    return IMAGE_SOURCE.findAll(html).map { it.groupValues[1].replace("&amp;", "&") }.toList()
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
