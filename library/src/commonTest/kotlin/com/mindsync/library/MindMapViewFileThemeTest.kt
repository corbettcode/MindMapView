package com.mindsync.library

import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.MapThemes
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MindMapViewFileThemeTest {
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("main", getRootNode().id, "Main")
    }

    @Test
    fun themeRoundTrips() {
        val tree = tree().apply { theme = MapThemes.byId("night") }

        assertEquals("night", MindMapViewFile.read(MindMapViewFile.write(tree)).theme.id)
    }

    @Test
    fun classicIsNotWritten() {
        assertFalse("theme=" in MindMapViewFile.write(tree()))
    }

    @Test
    fun unknownThemeReadsAsClassic() {
        val written = MindMapViewFile.write(tree().apply { theme = MapThemes.byId("night") })

        val tree = MindMapViewFile.read(written.replace("theme=\"night\"", "theme=\"nope\""))

        assertEquals(MapThemes.CLASSIC, tree.theme)
    }

    @Test
    fun freeMindGetsTheDrawnColors() {
        val tree = tree().apply { theme = MapThemes.byId("ocean") }
        val fill = (tree.fillOf("main").toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')

        assertTrue("BACKGROUND_COLOR=\"#$fill\"" in FreeMind.write(tree), fill)
    }
}
