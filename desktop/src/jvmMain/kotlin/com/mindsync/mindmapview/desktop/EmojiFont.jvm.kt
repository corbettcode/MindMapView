package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Read through a class of this module, as the thread's context class loader may be another one, or none.
private object EmojiFontResource

actual suspend fun loadEmojiFontFamily(
    file: String
): FontFamily? = withContext(Dispatchers.IO) {
    runCatching {
        EmojiFontResource::class.java
            .getResourceAsStream("/$file")
            ?.use { it.readBytes() }
            ?.let { bytes -> FontFamily(Font(identity = file, data = bytes)) }
    }.getOrNull()
}

internal actual val isEmojiFontFallback: Boolean = false
