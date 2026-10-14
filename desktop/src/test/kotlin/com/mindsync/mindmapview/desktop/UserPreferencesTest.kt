package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.NodeShape
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPreferencesTest {
    private val stored = mutableMapOf<String, String>()

    private fun store() = UserPreferencesStore(read = { stored[it] }, write = { name, value -> stored[name] = value })

    @Test
    fun preferencesThatWereNeverSetAreTheDefaults() {
        assertEquals(UserPreferences(), store().preferences)
    }

    @Test
    fun preferencesAreKeptFromOneRunToTheNext() {
        val changed = UserPreferences(ThemeMode.DARK, NodeShape.entries.last(), isRemoveConfirmed = false)
        assertEquals(changed, store().update(changed))
        assertEquals(changed, store().preferences)
    }

    @Test
    fun valueThatIsNotKnownIsTheDefault() {
        stored[UserPreferencesStore.THEME_MODE] = "SEPIA"
        stored[UserPreferencesStore.NEW_NODE_SHAPE] = "STAR"
        stored[UserPreferencesStore.IS_REMOVE_CONFIRMED] = "maybe"
        assertEquals(UserPreferences(), store().preferences)
    }
}
