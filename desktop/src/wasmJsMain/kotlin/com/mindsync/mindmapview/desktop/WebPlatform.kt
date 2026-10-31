@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.await
import kotlin.js.Promise
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val PREFERENCES_PREFIX = "mindmapview.preferences."

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

// Keys are joined by a character no file name has.
private fun recentKeys(joined: String): List<String> = if (joined.isEmpty()) emptyList() else joined.split('\u0000')

/**
 * The mind maps opened last, kept with their file handles. The browser never says which folder a file is in, so
 * a mind map is shown by its name alone.
 */
private object WebRecentMaps : RecentMaps {
    override var paths: List<String> = recentKeys(jsRecentMapKeys())
        private set

    override suspend fun add(document: MapDocument): List<String> = update(jsAddRecentMap(document.js))

    override suspend fun remove(path: String): List<String> = update(jsRemoveRecentMap(path))

    override fun documentAt(path: String): MapDocument = WebDocument(jsRecentMapFile(path))

    override suspend fun access(path: String): RecentMapAccess =
        when (jsRecentMapAccess(path).await<JsString>().toString()) {
            "available" -> RecentMapAccess.AVAILABLE
            "missing" -> RecentMapAccess.MISSING
            else -> RecentMapAccess.DENIED
        }

    override fun locationOf(path: String): String = fileNameOf(path)

    override fun folderOf(path: String): String? = null

    private suspend fun update(keys: Promise<JsString>): List<String> {
        paths = recentKeys(keys.await<JsString>().toString())
        return paths
    }
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

        override suspend fun chooseToOpen(
            current: MapDocument?
            , extensions: List<String>
        ): MapDocument? = withFileErrors {
            jsChooseFileToOpen(extensions.joinToString(",")).await<JsAny?>()?.let(::WebDocument)
        }

        override suspend fun chooseToSave(
            current: MapDocument?
            , newName: String
        ): MapDocument? = withFileErrors {
            val name = current?.name?.let(::mindMapViewFileName) ?: newName
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

    // Loaded before the app starts, as the app reads the paths once to start with.
    override val recentMaps: RecentMaps? = if (jsHasRecentMaps()) WebRecentMaps else null

    override fun encodePng(image: ImageBitmap): ByteArray =
        Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: throw MapFileException("the image could not be made into a PNG")

    override suspend fun openUserManual() {
        if (!jsOpenInNewTab(USER_MANUAL_FILE)) throw MapFileException("the browser did not allow a new tab")
    }
}
