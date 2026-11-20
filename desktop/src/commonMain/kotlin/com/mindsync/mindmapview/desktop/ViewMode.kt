package com.mindsync.mindmapview.desktop

/**
 * How the map is shown: as a map, as an outline, or as both side by side, each with the [label] of its menu item.
 *
 * Describes which main workspace view configuration is active.
 *
 * A view mode selects the map, the outliner, or both views together. The modes are ordered for cycling through
 * the available configurations, wrapping from the last mode back to the first.
 */
enum class ViewMode(
    val label: String
) {
    MAP("Map")
    , OUTLINER("Outliner")
    , BOTH("Map and Outliner");

    /** The mode Ctrl+Shift+O goes on to, round from the last back to the first. */
    val next: ViewMode get() = entries[(ordinal + 1) % entries.size]
}

/**
 * A move of the selected topics, as an outliner makes it, from the Edit menu or its keys.
 *
 * Describes a command that changes a node's position in the outline.
 *
 * The available moves cover reordering nodes among their siblings and changing their nesting level.
 * Each move provides a display label and, when available, the keyboard shortcut shown to the user.
 */
enum class OutlineMove(
    val label: String
    , val shortcut: String
) {
    UP("Move Up", "Alt+Shift+Up")
    , DOWN("Move Down", "Alt+Shift+Down")
    , INDENT("Indent", "")
    , OUTDENT("Outdent", "")
}

/** The share of the window's width the outline starts with, beside the map. */
const val DEFAULT_OUTLINE_FRACTION = 0.35f
