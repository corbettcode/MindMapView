package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mindsync.library.data.Tree
import com.mindsync.library.util.RichText
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.Uuid

private const val PRELOAD_TIMEOUT_MILLIS = 5_000L
// The address of an image from a file, which is shown from the mind map file it is stored in and from nowhere else.
private const val STORED_IMAGE_SCHEME = "mmv-image"


/**
 * Returns the bytes at the web address [url], or null when they cannot be loaded: the address may not be one
 * this platform may load.
 */
internal expect suspend fun fetchImageBytes(url: String): ByteArray?

/** Returns the image the encoded [bytes] are, or null when they are no image. */
internal expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/**
 * Loads the images of rich text from their web addresses. Rich text only makes room for an image that is
 * loaded when it is laid out, so the images of a note are loaded with [preload] before it is shown. An image
 * that cannot be loaded shows nothing: the address may not be an image. The images stored in a mind map file are
 * given with [addImages], so they show without their addresses being loaded.
 *
 * The rich text library has its image loading as experimental.
 */
@OptIn(ExperimentalRichTextApi::class)
internal object UrlImageLoader : ImageLoader {
    // Each image is fetched once, not every time a note that shows it is shown again, and so is a failure.
    // They are only changed on the main thread, where the composition is.
    private val images = mutableMapOf<String, ImageBitmap>()
    // The bytes each image was decoded from, for storing it in a mind map file.
    private val imageBytes = mutableMapOf<String, ByteArray>()
    private val failedUrls = mutableSetOf<String>()

    /** Makes [loadedImages], the bytes of each image by its address, the images at those addresses. */
    fun addImages(loadedImages: Map<String, ByteArray>) {
        loadedImages.forEach { (url, bytes) ->
            val image = decodeImage(bytes) ?: return@forEach
            images[url] = image
            imageBytes[url] = bytes
            failedUrls.remove(url)
        }
    }

    /**
     * Makes the image file [bytes] an image of its own, and returns the address it is shown from, or null when the
     * bytes are no image.
     */
    fun addImage(bytes: ByteArray): String? {
        if (decodeImage(bytes) == null) return null
        val url = "$STORED_IMAGE_SCHEME:${Uuid.random()}"
        addImages(mapOf(url to bytes))
        return url
    }

    /**
     * Returns the bytes of the images at [urls], by their addresses, loading those that are not loaded yet and
     * giving up on the rest after a while. An image that cannot be loaded is left out.
     */
    suspend fun bytesOf(urls: Collection<String>): Map<String, ByteArray> {
        withTimeoutOrNull(PRELOAD_TIMEOUT_MILLIS) {
            urls.forEach { url -> imageOf(url) }
        }
        return urls.mapNotNull { url -> imageBytes[url]?.let { url to it } }.toMap()
    }

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

    /** Returns the image at [url], loading it when it is not loaded yet, or null when it cannot be loaded. */
    suspend fun imageOf(url: String): ImageBitmap? {
        images[url]?.let { return it }
        if (url in failedUrls) return null
        val bytes = fetchImageBytes(url)
        val image = bytes?.let { decodeImage(it) }
        if (bytes == null || image == null) {
            failedUrls.add(url)
        }
        else {
            images[url] = image
            imageBytes[url] = bytes
        }
        return image
    }
}

/** Returns the addresses of the images in [html], in the order they are in it. */
internal fun imageSources(html: String): List<String> = RichText.imageSources(html)

/** Returns the addresses of the images in the text and notes of the nodes of [tree], each once. */
internal fun imageSources(tree: Tree<*>): Set<String> {
    val sources = mutableSetOf<String>()
    tree.doPreorderTraversal { node ->
        sources.addAll(imageSources(node.description))
        sources.addAll(imageSources(node.note))
    }
    return sources
}
