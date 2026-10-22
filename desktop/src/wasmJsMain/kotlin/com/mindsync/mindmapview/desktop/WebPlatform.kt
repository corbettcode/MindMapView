@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.await
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val PREFERENCES_PREFIX = "mindmapview.preferences."
private const val NEW_MAP_NAME = "mindmap"

/** A file the user chose in the browser, which [js] is the JavaScript object of. */
internal class WebDocument(
    val js: JsAny
) : MapDocument {
    override val name: String = jsFileName(js)
}

private val MapDocument.js: JsAny
    get() = (this as WebDocument).js

// A file that is chosen or written may fail in ways of the browser's own, which it says in a JavaScript error.
private suspend fun <T> withFileErrors(action: suspend () -> T): T = try {
    action()
} catch (e: Throwable) {
    throw MapFileException(e.message ?: "the browser did not allow it")
}

/**
 * The browser, where the user chooses each file to open, as a page may not open one by its path. Where the
 * browser has the File System Access API, a map is saved back to the file it was opened from; elsewhere it is
 * downloaded.
 */
internal object WebPlatform : AppPlatform {
    override val files: MapFiles = object : MapFiles {
        override fun isSavedTo(document: MapDocument): Boolean =
            jsHasHandle(document.js) && document.name.endsWith(MIND_MAP_VIEW_EXTENSION, ignoreCase = true)

        override suspend fun chooseToOpen(current: MapDocument?): MapDocument? = withFileErrors {
            jsChooseFileToOpen(OPENABLE_EXTENSIONS.joinToString(",")).await<JsAny?>()?.let(::WebDocument)
        }

        override suspend fun chooseToSave(current: MapDocument?): MapDocument? = withFileErrors {
            val name = mindMapViewFileName(current?.name ?: NEW_MAP_NAME)
            jsChooseFileToSave(name, "Mind maps", "application/x-mindmapview", MIND_MAP_VIEW_EXTENSION)
                .await<JsAny?>()
                ?.let(::WebDocument)
        }

        override suspend fun chooseToExport(
            current: MapDocument?
            , extension: String
        ): MapDocument? = withFileErrors {
            val name = exportFileName(current?.name ?: NEW_MAP_NAME, extension)
            val (description, mimeType) = when (extension) {
                PNG_EXTENSION -> "PNG images" to "image/png"
                HTML_EXTENSION -> "Web pages" to "text/html"
                else -> "FreeMind maps" to "application/x-freemind"
            }
            jsChooseFileToSave(name, description, mimeType, extension).await<JsAny?>()?.let(::WebDocument)
        }

        override suspend fun read(document: MapDocument): String = withFileErrors {
            jsReadFile(document.js).await<JsString>().toString()
        }

        override suspend fun write(
            document: MapDocument
            , content: ByteArray
        ) {
            withFileErrors { jsWriteFile(document.js, Base64.encode(content)).await<JsAny?>() }
        }
    }

    override val clipboard: SystemClipboard = object : SystemClipboard {
        override suspend fun setText(text: String): Boolean = jsWriteClipboard(text).await<JsBoolean>().toBoolean()

        override suspend fun text(): String? = jsReadClipboard().await<JsString?>()?.toString()

        // The browser only says what is on the clipboard when it is read, and may ask the user first.
        override fun hasText(): Boolean = true
    }

    override val preferencesStore: UserPreferencesStore by lazy {
        UserPreferencesStore(
            read = { jsStorageGet(PREFERENCES_PREFIX + it) }
            , write = { name, value -> jsStorageSet(PREFERENCES_PREFIX + name, value) }
        )
    }

    override val recentMaps: RecentMaps? = null

    override fun encodePng(image: ImageBitmap): ByteArray =
        Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: throw MapFileException("the image could not be made into a PNG")
}
