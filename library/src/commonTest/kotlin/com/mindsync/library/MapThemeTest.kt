package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.model.DrawInfo
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.contrastRatio
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapThemeTest {
    @Test
    fun classicIsTodaysLook() {
        val classic = MapThemes.CLASSIC

        assertEquals("classic", classic.id)
        listOf(classic.root, classic.mainTopic, classic.subtopic).forEach { level ->
            assertNull(level.fill)
            assertNull(level.text)
            assertEquals(NodeBorder(), level.border)
        }
        assertNull(classic.lineNodeText)
        assertEquals(Color.Black, classic.lineColor)
        assertEquals(DrawInfo().rootLineColors, classic.branchColors)
        assertEquals(Dp(5f), classic.lineWidth)
        assertEquals(Color.White, classic.background)
        assertNull(classic.fontName)
    }

    @Test
    fun thereAreSixThemesWithUniqueIds() {
        val ids = MapThemes.all.map { it.id }

        assertEquals(6, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(MapThemes.CLASSIC, MapThemes.all.first())
        assertTrue(ids.containsAll(listOf("ocean", "night", "paper")), ids.toString())
    }

    @Test
    fun unknownIdIsClassic() {
        assertEquals(MapThemes.CLASSIC, MapThemes.byId(null))
        assertEquals(MapThemes.CLASSIC, MapThemes.byId("nope"))
        assertEquals("ocean", MapThemes.byId("ocean").id)
    }

    @Test
    fun nodeTextReadsOnItsFill() {
        MapThemes.all.forEach { theme ->
            listOf(theme.root, theme.mainTopic, theme.subtopic).forEach { level ->
                val fill = level.fill
                val text = level.text
                if (fill != null && text != null) {
                    val ratio = contrastRatio(fill, text)
                    assertTrue(ratio >= 4.5f, "${theme.id}: $ratio between $fill and $text")
                }
            }
        }
    }

    @Test
    fun lineNodeTextReadsOnTheBackground() {
        MapThemes.all.drop(1).forEach { theme ->
            val text = assertNotNull(theme.lineNodeText, theme.id)
            val ratio = contrastRatio(text, theme.background)
            assertTrue(ratio >= 4.5f, "${theme.id}: $ratio")
        }
    }

    @Test
    fun nightIsDark() {
        assertTrue(MapThemes.byId("night").isDark)
        assertFalse(MapThemes.CLASSIC.isDark)
    }

    @Test
    fun fontsAreBundledOnes() {
        val bundled = setOf("Inter", "Lora", "Nunito", "Caveat", "JetBrains Mono")
        MapThemes.all.mapNotNull { it.fontName }.forEach { assertTrue(it in bundled, it) }
    }
}
