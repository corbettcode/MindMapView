package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.NodeShape
import com.mindsync.mindmapview.desktop.dialog.UserPreferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

val ThemeMode.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Keeps the [UserPreferences] from one run of the app to the next. They are read with [read], which returns
 * the value kept under a name or null when there is none, and written with [write] on every change.
 */
class UserPreferencesStore(
    read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    // A value that is no longer known, as one of a later version of the app may be, is the default.
    var preferences: UserPreferences = UserPreferences().let { defaults ->
        UserPreferences(
            themeMode = ThemeMode.entries.firstOrNull { it.name == read(THEME_MODE) } ?: defaults.themeMode
            , newNodeShape = NodeShape.entries.firstOrNull { it.name == read(NEW_NODE_SHAPE) } ?: defaults.newNodeShape
            , isRemoveConfirmed = read(IS_REMOVE_CONFIRMED)?.toBooleanStrictOrNull() ?: defaults.isRemoveConfirmed
            , nodeColors = read(NODE_COLORS)?.let(::parseColorList) ?: defaults.nodeColors
            , textColors = read(TEXT_COLORS)?.let(::parseColorList) ?: defaults.textColors
            , arrowLinkColors = read(ARROW_LINK_COLORS)?.let(::parseColorList) ?: defaults.arrowLinkColors
        )
    }
        private set

    fun update(newPreferences: UserPreferences): UserPreferences {
        preferences = newPreferences
        write(THEME_MODE, newPreferences.themeMode.name)
        write(NEW_NODE_SHAPE, newPreferences.newNodeShape.name)
        write(IS_REMOVE_CONFIRMED, newPreferences.isRemoveConfirmed.toString())
        write(NODE_COLORS, newPreferences.nodeColors.joinToString(",") { it.toHex() })
        write(TEXT_COLORS, newPreferences.textColors.joinToString(",") { it.toHex() })
        write(ARROW_LINK_COLORS, newPreferences.arrowLinkColors.joinToString(",") { it.toHex() })
        return preferences
    }

    companion object {
        const val THEME_MODE = "themeMode"
        const val NEW_NODE_SHAPE = "newNodeShape"
        const val IS_REMOVE_CONFIRMED = "isRemoveConfirmed"
        const val NODE_COLORS = "nodeColors"
        const val TEXT_COLORS = "textColors"
        const val ARROW_LINK_COLORS = "arrowLinkColors"
    }
}

private val HEX_COLOR = Regex("#?([0-9A-Fa-f]{6})")

/** The color as `#RRGGBB`. A node is filled with an opaque color, so it has no alpha. */
fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** The opaque color written as `#RRGGBB` or `RRGGBB`, or null when [text] is not one. */
fun parseHexColor(
    text: String
): Color? = HEX_COLOR.matchEntire(text.trim())
    ?.let { Color(0xFF000000 or it.groupValues[1].toLong(16)) }

// A list with a color that is not known, or with no colors at all, is not one the user chose.
private fun parseColorList(
    text: String
): List<Color>? = text.split(",")
    .map { parseHexColor(it) ?: return null }
    .takeIf { it.isNotEmpty() }
