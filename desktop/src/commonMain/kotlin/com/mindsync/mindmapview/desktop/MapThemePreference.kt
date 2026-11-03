package com.mindsync.mindmapview.desktop

import com.mindsync.library.MindMapState
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapTheme
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

/**
 * Returns these preferences with what [theme] sets in place of their own: its colors, its background, and its
 * font, ways of showing attributes and notes, and shape of a new node where it has them. The theme new maps start in stays as it was.
 */
internal fun UserPreferences.withTheme(
    theme: MapTheme
): UserPreferences = copy(
    arrowLinkColors = themeArrowLinkColors(theme)
    , attributeDisplay = theme.attributeDisplay ?: attributeDisplay
    , backgroundColor = null
    , nodeColors = themeFillColors(theme)
    , newNodeShape = theme.newNodeShape ?: newNodeShape
    , nodeFont = nodeFontFor(theme, nodeFont)
    , noteDisplay = theme.noteDisplay ?: noteDisplay
    , textColors = themeTextColors(theme)
)

/**
 * Returns the preferences to keep once [tree] is opened with the [saved] preferences of its file: those it was
 * saved with, or, for a file that has none, those of its theme, as when the theme is chosen.
 */
internal fun UserPreferences.openedWith(
    tree: Tree<*>
    , saved: Map<String, String>?
): UserPreferences = saved?.let { withMapValues(it) } ?: withTheme(tree.theme)
