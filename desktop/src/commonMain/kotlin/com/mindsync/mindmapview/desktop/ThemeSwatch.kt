package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color

/**
 * The color a node starts with, [default], which follows the map's theme, and the color the theme draws it in,
 * [themed], or null where the theme leaves it as it is. A color picker shows the theme's color for the default
 * and, when that is chosen, keeps the default, for the node to go on following the theme.
 */
internal class ThemeSwatch(
    val default: Color
    , val themed: Color?
) {
    fun shown(color: Color): Color = if (color == default && themed != null) themed else color

    fun stored(color: Color): Color = if (themed != null && color == themed) default else color
}
