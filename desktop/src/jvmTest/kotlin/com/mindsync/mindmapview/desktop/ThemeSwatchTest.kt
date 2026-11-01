package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeSwatchTest {
    private val purple = Color(0xFF8E7CC3)
    private val cyan = Color(0xFF20A4C3)
    private val coral = Color(0xFFFF7F50)

    @Test
    fun defaultIsShownInTheThemesColor() {
        val swatch = ThemeSwatch(default = purple, themed = cyan)

        assertEquals(cyan, swatch.shown(purple))
        assertEquals(coral, swatch.shown(coral))
    }

    @Test
    fun choosingTheThemesColorStoresTheDefault() {
        val swatch = ThemeSwatch(default = purple, themed = cyan)

        assertEquals(purple, swatch.stored(cyan))
        assertEquals(coral, swatch.stored(coral))
    }

    @Test
    fun withoutAThemedColorTheDefaultIsShown() {
        val swatch = ThemeSwatch(default = purple, themed = null)

        assertEquals(purple, swatch.shown(purple))
        assertEquals(purple, swatch.stored(purple))
    }
}
