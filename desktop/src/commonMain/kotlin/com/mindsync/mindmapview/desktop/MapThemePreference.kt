package com.mindsync.mindmapview.desktop

import com.mindsync.library.MindMapState
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapThemes
import com.mindsync.mindmapview.desktop.dialog.UserPreferences

/** Returns an empty map in the theme new maps start in. */
internal fun newMapTree(preferences: UserPreferences): Tree<Any> =
    Tree<Any>().apply { theme = MapThemes.byId(preferences.mapThemeId) }

/**
 * Takes the preferences [submitted] in the dialog, which [opened] with the theme of the open map in [state], and
 * returns those to keep. A map theme that was changed in the dialog is given to the open map, as a change that can be
 * undone, and kept for new maps. One that was left as it was keeps the [remembered] one, as it only showed the theme
 * of the open map.
 */
internal fun submitPreferences(
    state: MindMapState
    , opened: UserPreferences
    , submitted: UserPreferences
    , remembered: UserPreferences
): UserPreferences {
    if (submitted.mapThemeId == opened.mapThemeId) return submitted.copy(mapThemeId = remembered.mapThemeId)
    state.setTheme(MapThemes.byId(submitted.mapThemeId))
    return submitted
}
