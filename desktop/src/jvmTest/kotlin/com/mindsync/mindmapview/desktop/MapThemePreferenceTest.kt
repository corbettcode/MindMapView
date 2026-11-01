package com.mindsync.mindmapview.desktop

import com.mindsync.library.MindMapState
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapThemes
import com.mindsync.mindmapview.desktop.dialog.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class MapThemePreferenceTest {
    private val stored = mutableMapOf<String, String>()

    private fun store() = UserPreferencesStore(read = { stored[it] }, write = { name, value -> stored[name] = value })

    private fun stateIn(themeId: String): MindMapState {
        val tree = Tree<Any>().apply { theme = MapThemes.byId(themeId) }
        return MindMapState(tree).apply { setTree(tree) }
    }

    @Test
    fun mapThemeIsKeptFromOneRunToTheNext() {
        store().update(UserPreferences(mapThemeId = "ocean"))

        assertEquals("ocean", store().preferences.mapThemeId)
    }

    @Test
    fun newMapsAreClassicUntilAThemeIsChosen() {
        assertEquals(MapThemes.CLASSIC, newMapTree(UserPreferences()).theme)
    }

    @Test
    fun newMapStartsInTheRememberedTheme() {
        assertEquals("ocean", newMapTree(UserPreferences(mapThemeId = "ocean")).theme.id)
    }

    @Test
    fun choosingAThemeChangesTheOpenMapAndIsRemembered() {
        val state = stateIn("classic")

        val kept = submitPreferences(
            state
            , opened = UserPreferences(mapThemeId = "classic")
            , submitted = UserPreferences(mapThemeId = "ocean")
            , remembered = UserPreferences()
        )

        assertEquals("ocean", state.theme.id)
        assertEquals("ocean", kept.mapThemeId)
        state.undo()
        assertEquals("classic", state.theme.id)
    }

    @Test
    fun leavingTheThemeAsItWasKeepsTheRememberedOne() {
        // A map saved in Night is open; the dialog opened on Night, and only the font was changed.
        val state = stateIn("night")

        val kept = submitPreferences(
            state
            , opened = UserPreferences(mapThemeId = "night")
            , submitted = UserPreferences(mapThemeId = "night", nodeFont = NodeFont.LORA)
            , remembered = UserPreferences(mapThemeId = "ocean")
        )

        assertEquals("night", state.theme.id)
        assertEquals("ocean", kept.mapThemeId)
        assertEquals(NodeFont.LORA, kept.nodeFont)
    }
}
