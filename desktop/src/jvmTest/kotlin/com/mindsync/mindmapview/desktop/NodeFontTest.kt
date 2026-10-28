package com.mindsync.mindmapview.desktop

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NodeFontTest {
    @Test
    fun choicesAreInOrder() = assertEquals(
        listOf("Default", "Inter", "Lora", "Nunito", "Caveat", "JetBrains Mono")
        , NodeFont.entries.map { it.label }
    )

    // JUnit only runs test methods that return nothing, hence runBlocking<Unit>.
    @Test
    fun defaultHasNoFont() = runBlocking<Unit> { assertNull(loadNodeFontFamily(NodeFont.DEFAULT)) }

    @Test
    fun everyBundledFontLoads() = runBlocking<Unit> {
        NodeFont.entries.drop(1).forEach { assertNotNull(loadNodeFontFamily(it), it.label) }
    }

    @Test
    fun everyFontHasItsFaces() = NodeFont.entries.drop(1).forEach { font ->
        val faces = font.files.map { it.weight to it.style }.toSet()
        val upright = setOf(FontWeight.Normal to FontStyle.Normal, FontWeight.Bold to FontStyle.Normal)
        val italic = setOf(FontWeight.Normal to FontStyle.Italic, FontWeight.Bold to FontStyle.Italic)
        assertEquals(if (font == NodeFont.CAVEAT) upright else upright + italic, faces, font.label)
    }

    // Each file is read with Skia: it must be a font of that family, with the weight and slant it is listed as.
    @Test
    fun everyBundledFileIsTheRightFace() = runBlocking<Unit> {
        NodeFont.entries.flatMap { font -> font.files.map { font to it } }.forEach { (font, face) ->
            val bytes = assertNotNull(readBundledFile(face.file), face.file)
            val typeface = assertNotNull(FontMgr.default.makeFromData(Data.makeFromBytes(bytes)), face.file)
            assertEquals(font.label, typeface.familyName, face.file)
            assertEquals(face.weight == FontWeight.Bold, typeface.isBold, face.file)
            assertEquals(face.style == FontStyle.Italic, typeface.isItalic, face.file)
        }
    }

    // A font is only read once it is drawn with, so bytes that are not a font must be turned down beforehand.
    @Test
    fun bytesThatAreNotAFontGiveNull() = assertNull(bundledFont("x.ttf", "not a font".encodeToByteArray()))

    @Test
    fun missingFileGivesNull() = runBlocking<Unit> { assertNull(readBundledFile("missing.ttf")) }
}
