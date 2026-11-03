@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.await
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val WEB_ADDRESS = Regex("^https?://", RegexOption.IGNORE_CASE)

// The browser only fetches an image from another site when that site allows it, which many do not.
internal actual suspend fun fetchImageBytes(url: String): ByteArray? {
    if (!WEB_ADDRESS.containsMatchIn(url)) return null
    val base64 = jsFetchBase64(url).await<JsString?>()?.toString() ?: return null
    return try {
        Base64.decode(base64)
    } catch (e: IllegalArgumentException) {
        null
    }
}

internal actual fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (e: IllegalArgumentException) {
    null
}
