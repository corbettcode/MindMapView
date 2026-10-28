package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

// The font ships with the app, as on Linux plain fonts are chosen before the system's color emoji font and the
// web app has no system fonts at all.
const val EMOJI_FONT_FILE = "NotoColorEmoji.ttf"

/** The bytes of a font file that ships with the app, or null when it cannot be read. */
expect suspend fun readBundledFile(file: String): ByteArray?

/** The font in [data], read from the bundled [file]. */
expect fun bundledFont(
    file: String
    , data: ByteArray
    , weight: FontWeight = FontWeight.Normal
    , style: FontStyle = FontStyle.Normal
): Font

/** The bundled color emoji font, or null when it cannot be read. */
suspend fun loadEmojiFontFamily(
    file: String = EMOJI_FONT_FILE
): FontFamily? = readBundledFile(file)
    ?.let { bytes -> runCatching { FontFamily(bundledFont(file, bytes)) }.getOrNull() }

/** The color emoji font once loaded, for text drawn with emojis in it. */
val LocalEmojiFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/**
 * Whether all other text falls back to the emoji font for the emojis it has no glyphs for, as where there are no
 * system fonts to fall back to.
 */
internal expect val isEmojiFontFallback: Boolean
