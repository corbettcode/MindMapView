package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun loadEmojiFontFamily(
    file: String
): FontFamily? = withContext(Dispatchers.IO) {
    runCatching {
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(file)
            ?.use { it.readBytes() }
            ?.let { bytes -> FontFamily(Font(identity = file, data = bytes)) }
    }.getOrNull()
}

internal actual val isEmojiFontFallback: Boolean = false
