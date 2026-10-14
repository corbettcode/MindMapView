package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.NodeShape
import java.util.prefs.Preferences

internal enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * What the user chose in the preferences dialog. Each of them starts out as the app was before there were
 * any preferences.
 */
internal data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // The shape of a node that is added to a parent without children, which has no last child to look like.
    val newNodeShape: NodeShape = NodeShape.RECTANGLE,
    val isRemoveConfirmed: Boolean = true,
)

/**
 * Keeps the [UserPreferences] from one run of the app to the next. They are read with [read], which returns
 * the value kept under a name or null when there is none, and written with [write] on every change.
 */
internal class UserPreferencesStore(
    read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    // A value that is no longer known, as one of a later version of the app may be, is the default.
    var preferences: UserPreferences = UserPreferences().let { defaults ->
        UserPreferences(
            themeMode = ThemeMode.entries.firstOrNull { it.name == read(THEME_MODE) } ?: defaults.themeMode
            , newNodeShape = NodeShape.entries.firstOrNull { it.name == read(NEW_NODE_SHAPE) } ?: defaults.newNodeShape
            , isRemoveConfirmed = read(IS_REMOVE_CONFIRMED)?.toBooleanStrictOrNull() ?: defaults.isRemoveConfirmed
        )
    }
        private set

    fun update(newPreferences: UserPreferences): UserPreferences {
        preferences = newPreferences
        write(THEME_MODE, newPreferences.themeMode.name)
        write(NEW_NODE_SHAPE, newPreferences.newNodeShape.name)
        write(IS_REMOVE_CONFIRMED, newPreferences.isRemoveConfirmed.toString())
        return preferences
    }

    companion object {
        const val THEME_MODE = "themeMode"
        const val NEW_NODE_SHAPE = "newNodeShape"
        const val IS_REMOVE_CONFIRMED = "isRemoveConfirmed"

        /** The preferences of the user, kept in the preferences of the user that Java keeps. */
        fun ofUser(): UserPreferencesStore {
            val node = Preferences.userNodeForPackage(UserPreferencesStore::class.java).node("preferences")
            return UserPreferencesStore(read = { node.get(it, null) }, write = { name, value -> node.put(name, value) })
        }
    }
}
