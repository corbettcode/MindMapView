@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// A font is served next to index.html, so its file name is its address.
actual suspend fun readBundledFile(
    file: String
): ByteArray? = jsFetchBase64(file)
    .await<JsString?>()
    ?.toString()
    ?.let { base64 -> runCatching { Base64.decode(base64) }.getOrNull() }

actual fun bundledFont(
    file: String
    , data: ByteArray
    , weight: FontWeight
    , style: FontStyle
): Font? = data
    .takeIf { runCatching { FontMgr.default.makeFromData(Data.makeFromBytes(it)) }.getOrNull() != null }
    ?.let { androidx.compose.ui.text.platform.Font(identity = file, data = it, weight = weight, style = style) }

internal actual val isEmojiFontFallback: Boolean = true
