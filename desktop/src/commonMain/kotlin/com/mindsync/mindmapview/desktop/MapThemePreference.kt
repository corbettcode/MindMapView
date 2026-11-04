package com.mindsync.mindmapview.desktop

import com.mindsync.library.MindMapState
import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapTheme
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.TopicKind
import com.mindsync.mindmapview.desktop.dialog.TopicPreferences
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
 * Returns these preferences with what [theme] sets in place of their own: its colors, its background, the style
 * of each kind of topic, and its font, ways of showing attributes and notes, and shapes of new nodes where it has
 * them. The theme new maps start in stays as it was.
 */
internal fun UserPreferences.withTheme(
    theme: MapTheme
): UserPreferences = copy(
    arrowLinkColors = themeArrowLinkColors(theme)
    , attributeDisplay = theme.attributeDisplay ?: attributeDisplay
    , backgroundColor = null
    , noteDisplay = theme.noteDisplay ?: noteDisplay
    , rootTopic = rootTopic.withTheme(theme, TopicKind.ROOT_TOPIC)
    , subtopic = subtopic.withTheme(theme, TopicKind.SUBTOPIC)
    , topic = topic.withTheme(theme, TopicKind.TOPIC)
)

/** Returns these preferences of a kind of topic with what [theme] sets for the nodes of [kind]. */
internal fun TopicPreferences.withTheme(
    theme: MapTheme
    , kind: TopicKind
): TopicPreferences = copy(
    font = nodeFontFor(theme, font)
    , shape = theme.newNodeShapeOf(kind) ?: shape
    , style = theme.levelStyleOf(kind)
    , fillColors = themeFillColors(theme)
    , textColors = themeTextColors(theme)
)

/**
 * Returns the preferences to keep once [tree] is opened with the [saved] preferences of its file: those it was
 * saved with, and those of its theme, as when the theme is chosen, for any it was not saved with, as a file saved
 * before maps kept them, or before each kind of topic had its own, was not.
 */
internal fun UserPreferences.openedWith(
    tree: Tree<*>
    , saved: Map<String, String>?
): UserPreferences = withTheme(tree.theme).let { themed ->
    saved?.let { themed.withMapValues(it, tree.theme) } ?: themed
}
