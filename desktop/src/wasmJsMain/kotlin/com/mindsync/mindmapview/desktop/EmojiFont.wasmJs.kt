@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// The font is served next to index.html, so its file name is its address.
actual suspend fun loadEmojiFontFamily(
    file: String
): FontFamily? = jsFetchBase64(file)
    .await<JsString?>()
    ?.toString()
    ?.let { base64 ->
        runCatching { FontFamily(Font(identity = file, data = Base64.decode(base64))) }.getOrNull()
    }

internal actual val isEmojiFontFallback: Boolean = true
