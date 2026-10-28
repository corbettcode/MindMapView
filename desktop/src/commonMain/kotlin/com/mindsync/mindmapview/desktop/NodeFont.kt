package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** A bundled font [file] with the [weight] and [style] it draws. */
data class NodeFontFile(
    val file: String
    , val weight: FontWeight
    , val style: FontStyle
)

/**
 * The fonts node text can be drawn in. [DEFAULT] is the platform's own font; the others ship with the app, as
 * the web app has no fonts of the computer to offer.
 */
enum class NodeFont(
    val label: String
    , val files: List<NodeFontFile>
) {
    DEFAULT("Default", emptyList())
    , INTER("Inter", fontFiles("Inter"))
    , LORA("Lora", fontFiles("Lora"))
    , NUNITO("Nunito", fontFiles("Nunito"))
    // Caveat has no italic, so its italic is slanted from the upright faces.
    , CAVEAT("Caveat", fontFiles("Caveat", hasItalic = false))
    , JETBRAINS_MONO("JetBrains Mono", fontFiles("JetBrainsMono"))
}

private fun fontFiles(
    name: String
    , hasItalic: Boolean = true
): List<NodeFontFile> = listOfNotNull(
    NodeFontFile("$name-Regular.ttf", FontWeight.Normal, FontStyle.Normal)
    , NodeFontFile("$name-Bold.ttf", FontWeight.Bold, FontStyle.Normal)
    , NodeFontFile("$name-Italic.ttf", FontWeight.Normal, FontStyle.Italic).takeIf { hasItalic }
    , NodeFontFile("$name-BoldItalic.ttf", FontWeight.Bold, FontStyle.Italic).takeIf { hasItalic }
)

/** The family of [font], or null for [NodeFont.DEFAULT] or when one of its files cannot be read. */
suspend fun loadNodeFontFamily(
    font: NodeFont
): FontFamily? = font.files
    .takeIf { it.isNotEmpty() }
    ?.map { face ->
        val bytes = readBundledFile(face.file) ?: return null
        runCatching { bundledFont(face.file, bytes, face.weight, face.style) }.getOrNull() ?: return null
    }
    ?.let { FontFamily(it) }
