package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import kotlinx.coroutines.withTimeoutOrNull

private const val PRELOAD_TIMEOUT_MILLIS = 5_000L
private val IMAGE_SOURCE = Regex("""<img\s[^>]*?src="([^"]*)"""", RegexOption.IGNORE_CASE)

/**
 * Returns the image at the web address [url], or null when it cannot be loaded: the address may not be an
 * image, or not one this platform may load.
 */
internal expect suspend fun fetchImage(url: String): ImageBitmap?

/**
 * Loads the images of rich text from their web addresses. Rich text only makes room for an image that is
 * loaded when it is laid out, so the images of a note are loaded with [preload] before it is shown. An image
 * that cannot be loaded shows nothing: the address may not be an image.
 *
 * The rich text library has its image loading as experimental.
 */
@OptIn(ExperimentalRichTextApi::class)
internal object UrlImageLoader : ImageLoader {
    // Each image is fetched once, not every time a note that shows it is shown again, and so is a failure.
    // They are only changed on the main thread, where the composition is.
    private val images = mutableMapOf<String, ImageBitmap>()
    private val failedUrls = mutableSetOf<String>()

    /** Loads the images in [html] that are not loaded yet, giving up on the rest after a while. */
    suspend fun preload(html: String) {
        withTimeoutOrNull(PRELOAD_TIMEOUT_MILLIS) {
            imageSources(html).forEach { url -> imageOf(url) }
        }
    }

    @Composable
    override fun load(model: Any): ImageData? {
        val url = model.toString()
        val image by produceState(images[url], url) {
            if (value == null) value = imageOf(url)
        }
        return image?.let { ImageData(painter = BitmapPainter(it)) }
    }

    private suspend fun imageOf(url: String): ImageBitmap? {
        images[url]?.let { return it }
        if (url in failedUrls) return null
        val image = fetchImage(url)
        if (image == null) failedUrls.add(url) else images[url] = image
        return image
    }
}

/** Returns the addresses of the images in [html], in the order they are in it. */
internal fun imageSources(html: String): List<String> {
    return IMAGE_SOURCE.findAll(html).map { it.groupValues[1].replace("&amp;", "&") }.toList()
}
