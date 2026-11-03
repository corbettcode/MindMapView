package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.model.MapTheme

/**
 * Returns the fill colors offered for a map in [theme]: those of its levels and then its branch colors. A theme
 * that leaves nodes in the colors they have, as Classic does, offers the default fill colors.
 */
internal fun themeFillColors(
    theme: MapTheme
): List<Color> = listOfNotNull(theme.root.fill, theme.mainTopic.fill, theme.subtopic.fill)
    .takeIf { it.isNotEmpty() }
    ?.let { (it + theme.branchColors).distinct() }
    ?: DEFAULT_NODE_COLORS

/**
 * Returns the text colors offered for a map in [theme]: those of its levels and its lines, and then the default
 * text colors, for a color the theme has none like. A theme that leaves nodes in the colors they have, as Classic
 * does, offers the default text colors alone.
 */
internal fun themeTextColors(
    theme: MapTheme
): List<Color> = listOfNotNull(theme.root.text, theme.mainTopic.text, theme.subtopic.text, theme.lineNodeText)
    .takeIf { it.isNotEmpty() }
    ?.let { (it + theme.lineColor + DEFAULT_TEXT_COLORS).distinct() }
    ?: DEFAULT_TEXT_COLORS

/**
 * Returns the arrow link colors offered for a map in [theme]: its own, or the default arrow link colors for a theme
 * without any, as Classic is.
 */
internal fun themeArrowLinkColors(
    theme: MapTheme
): List<Color> = theme.arrowLinkColors ?: DEFAULT_ARROW_LINK_COLORS
