package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.io.MindMapViewFile
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.LevelStyle
import com.mindsync.library.model.NoteDisplay
import com.mindsync.library.model.TopicKind
import com.mindsync.mindmapview.desktop.dialog.TopicPreferences
import com.mindsync.mindmapview.desktop.dialog.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapFilePreferencesTest {
    private val saved = UserPreferences(
        arrowLinkColors = listOf(Color(0xFF111111))
        , attributeDisplay = AttributeDisplay.FLYOVER
        , backgroundColor = Color(0xFF223344)
        , emojiTones = mapOf("👍" to "👍🏽")
        , isRemoveConfirmed = false
        , mapThemeId = "night"
        , nodeFont = NodeFont.CAVEAT
        , noteDisplay = NoteDisplay.INLINE
        , topic = TopicPreferences(
            shape = NodeShape.entries.last()
            , style = LevelStyle(fill = Color(0xFF445566), text = Color(0xFF000000))
            , fillColors = listOf(Color(0xFF123456))
            , textColors = listOf(Color(0xFFABCDEF))
        )
    )

    private fun tree(themeId: String = "classic"): Tree<Any> = Tree<Any>().apply { theme = MapThemes.byId(themeId) }

    @Test
    fun mapFileKeepsOnlyThePreferencesOfTheMap() {
        assertEquals(UserPreferencesStore.MAP_PREFERENCES, saved.mapValues().keys)
    }

    @Test
    fun openingAMapTakesThePreferencesItWasSavedWith() {
        val content = MindMapViewFile.write(tree(), preferences = saved.mapValues())

        val opened = UserPreferences().openedWith(tree(), readMapPreferences("a.mmv", content))

        // How the app works and the theme new maps start in are not the map's.
        assertEquals(saved.copy(emojiTones = emptyMap(), isRemoveConfirmed = true, mapThemeId = "classic"), opened)
    }

    @Test
    fun mapSavedWithNoBackgroundColorIsDrawnOnItsTheme() {
        val content = MindMapViewFile.write(tree(), preferences = UserPreferences().mapValues())

        val opened = saved.openedWith(tree(), readMapPreferences("a.mmv", content))

        assertNull(opened.backgroundColor)
    }

    @Test
    fun mapWithoutPreferencesTakesThoseOfItsTheme() {
        val paper = MapThemes.byId("paper")
        val content = MindMapViewFile.write(tree("paper"))

        val opened = saved.openedWith(tree("paper"), readMapPreferences("a.mmv", content))

        assertEquals(saved.withTheme(paper), opened)
        assertEquals(NodeFont.LORA, opened.nodeFont)
        assertNull(opened.backgroundColor)
        assertEquals(themeArrowLinkColors(paper), opened.arrowLinkColors)
    }

    @Test
    fun mapSavedBeforeEachTopicHadItsOwnTakesItsThemesStyles() {
        val ocean = MapThemes.byId("ocean")
        val content = MindMapViewFile.write(
            tree("ocean")
            , preferences = mapOf(
                UserPreferencesStore.NODE_COLORS to "#123456"
                , UserPreferencesStore.NOTE_DISPLAY to "INLINE"
            )
        )

        val opened = UserPreferences().openedWith(tree("ocean"), readMapPreferences("a.mmv", content))

        assertEquals(ocean.mainTopic, opened.topic.style)
        assertEquals(listOf(Color(0xFF123456)), opened.subtopic.fillColors)
        assertEquals(NoteDisplay.INLINE, opened.noteDisplay)
    }

    @Test
    fun freeMindMapHasNoPreferences() {
        assertNull(readMapPreferences("a.mm", FreeMind.write(tree())))
    }

    @Test
    fun choosingAThemeSetsWhatItHas() {
        val ocean = MapThemes.byId("ocean")

        val chosen = saved.withTheme(ocean)

        TopicKind.entries.forEach { kind ->
            assertEquals(ocean.levelStyleOf(kind), chosen.topicOf(kind).style)
            assertEquals(themeFillColors(ocean), chosen.topicOf(kind).fillColors)
            assertEquals(themeTextColors(ocean), chosen.topicOf(kind).textColors)
        }
        assertEquals(themeArrowLinkColors(ocean), chosen.arrowLinkColors)
        assertNull(chosen.backgroundColor)
        assertEquals(ocean.newNodeShape, chosen.topic.shape)
        assertEquals(ocean.newNodeShape, chosen.subtopic.shape)
        assertEquals(ocean.noteDisplay, chosen.noteDisplay)
        // Ocean has no font or way of showing attributes of its own, so those stay.
        assertEquals(NodeFont.CAVEAT, chosen.nodeFont)
        assertEquals(AttributeDisplay.FLYOVER, chosen.attributeDisplay)
    }

    @Test
    fun highContrastAddsSubtopicsAsLines() {
        val chosen = saved.withTheme(MapThemes.byId("contrast"))

        assertEquals(NodeShape.RECTANGLE, chosen.topic.shape)
        assertEquals(NodeShape.LINE, chosen.subtopic.shape)
    }

    @Test
    fun classicLeavesTheShapeAndNoteDisplayAsTheyAre() {
        val chosen = saved.withTheme(MapThemes.CLASSIC)

        assertEquals(saved.topic.shape, chosen.topic.shape)
        assertEquals(saved.noteDisplay, chosen.noteDisplay)
    }
}
