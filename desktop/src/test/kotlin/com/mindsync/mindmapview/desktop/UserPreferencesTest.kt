package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeShape
import com.mindsync.mindmapview.desktop.dialog.UserPreferences
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
        val changed = UserPreferences(
            ThemeMode.DARK
            , NodeShape.entries.last()
            , isRemoveConfirmed = false
            , nodeColors = listOf(Color(0xFF123456), Color.White)
            , textColors = listOf(Color(0xFFABCDEF))
        )
        assertEquals(changed, store().update(changed))
        assertEquals(changed, store().preferences)
    }

    @Test
    fun valueThatIsNotKnownIsTheDefault() {
        stored[UserPreferencesStore.THEME_MODE] = "SEPIA"
        stored[UserPreferencesStore.NEW_NODE_SHAPE] = "STAR"
        stored[UserPreferencesStore.IS_REMOVE_CONFIRMED] = "maybe"
        stored[UserPreferencesStore.NODE_COLORS] = "#123456,teal"
        stored[UserPreferencesStore.TEXT_COLORS] = "#12"
        assertEquals(UserPreferences(), store().preferences)
    }

    @Test
    fun noNodeColorsAreTheDefaultNodeColors() {
        stored[UserPreferencesStore.NODE_COLORS] = ""
        assertEquals(DEFAULT_NODE_COLORS, store().preferences.nodeColors)
    }

    @Test
    fun hexColorIsReadWithOrWithoutItsHash() {
        assertEquals(Color(0xFF2D5728), parseHexColor("#2d5728"))
        assertEquals(Color(0xFF2D5728), parseHexColor("2D5728"))
        assertEquals("#2D5728", Color(0xFF2D5728).toHex())
        assertEquals(null, parseHexColor("#2D57"))
    }
}
