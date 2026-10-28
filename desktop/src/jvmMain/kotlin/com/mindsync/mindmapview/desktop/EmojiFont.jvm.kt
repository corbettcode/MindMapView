package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Read through a class of this module, as the thread's context class loader may be another one, or none.
private object BundledFontResource

actual suspend fun readBundledFile(
    file: String
): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        BundledFontResource::class.java
            .getResourceAsStream("/$file")
            ?.use { it.readBytes() }
    }.getOrNull()
}

actual fun bundledFont(
    file: String
    , data: ByteArray
    , weight: FontWeight
    , style: FontStyle
): Font? = data
    .takeIf { runCatching { FontMgr.default.makeFromData(Data.makeFromBytes(it)) }.getOrNull() != null }
    ?.let { androidx.compose.ui.text.platform.Font(identity = file, data = it, weight = weight, style = style) }

internal actual val isEmojiFontFallback: Boolean = false
