package com.mindsync.mindmapview.desktop

import com.mindsync.library.model.MapThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeColorsTest {
    @Test
    fun classicOffersTheDefaultColors() {
        assertEquals(DEFAULT_NODE_COLORS, themeFillColors(MapThemes.CLASSIC))
        assertEquals(DEFAULT_TEXT_COLORS, themeTextColors(MapThemes.CLASSIC))
    }

    @Test
    fun aThemeOffersItsOwnColorsFirst() {
        val ocean = MapThemes.byId("ocean")
        val fills = themeFillColors(ocean)
        assertEquals(listOfNotNull(ocean.root.fill, ocean.mainTopic.fill, ocean.subtopic.fill), fills.take(3))
        assertTrue(fills.containsAll(ocean.branchColors))
        assertEquals(fills.distinct(), fills)

        val texts = themeTextColors(ocean)
        assertEquals(ocean.root.text, texts.first())
        assertTrue(texts.containsAll(DEFAULT_TEXT_COLORS))
        assertEquals(texts.distinct(), texts)
    }
}
