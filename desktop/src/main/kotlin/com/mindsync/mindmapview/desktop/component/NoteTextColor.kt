package com.mindsync.mindmapview.desktop.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The color of the text of a note that has none of its own: white in dark mode, where the theme's text is a
 * light gray, and the theme's text color otherwise.
 */
@Composable
fun noteTextColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE) Color.White
    else MaterialTheme.colorScheme.onSurface

private const val DARK_SURFACE_LUMINANCE = 0.5f
