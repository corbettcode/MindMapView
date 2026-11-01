package com.mindsync.mindmapview.desktop

import com.mindsync.library.model.MapThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeFontTest {
    @Test
    fun classicUsesThePreference() {
        assertEquals(NodeFont.LORA, nodeFontFor(MapThemes.CLASSIC, NodeFont.LORA))
    }

    @Test
    fun themeFontWins() {
        assertEquals(NodeFont.LORA, nodeFontFor(MapThemes.byId("paper"), NodeFont.INTER))
    }

    @Test
    fun everyThemeFontIsANodeFont() {
        MapThemes.all.mapNotNull { it.fontName }.forEach { name ->
            assertTrue(NodeFont.entries.any { it.label == name }, name)
        }
    }
}
