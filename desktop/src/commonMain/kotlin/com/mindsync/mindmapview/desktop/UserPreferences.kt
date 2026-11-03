package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.NodeShape
import com.mindsync.library.emoji.EmojiCatalog
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.MapTheme
import com.mindsync.library.model.NoteDisplay
import com.mindsync.mindmapview.desktop.dialog.UserPreferences

/**
 * Returns how attributes are shown on a map in [theme]: the theme's own way, or the way the app is set to,
 * [preference], for a theme without one.
 */
internal fun attributeDisplayFor(
    theme: MapTheme
    , preference: AttributeDisplay
): AttributeDisplay = theme.attributeDisplay ?: preference

/**
 * Keeps the [UserPreferences] from one run of the app to the next. They are read with [read], which returns
 * the value kept under a name or null when there is none, and written with [write] on every change.
 */
class UserPreferencesStore(
    read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    var preferences: UserPreferences = readUserPreferences(read, UserPreferences())
        private set

    fun update(newPreferences: UserPreferences): UserPreferences {
        preferences = newPreferences
        newPreferences.values().forEach { (name, value) -> write(name, value) }
        return preferences
    }

    companion object {
        const val NEW_NODE_SHAPE = "newNodeShape"
        const val IS_REMOVE_CONFIRMED = "isRemoveConfirmed"
        const val NODE_COLORS = "nodeColors"
        const val TEXT_COLORS = "textColors"
        const val ARROW_LINK_COLORS = "arrowLinkColors"
        const val EMOJI_TONES = "emojiTones"
        const val NODE_FONT = "nodeFont"
        const val NOTE_DISPLAY = "noteDisplay"
        const val ATTRIBUTE_DISPLAY = "attributeDisplay"
        const val MAP_THEME = "mapTheme"
        const val BACKGROUND_COLOR = "backgroundColor"

        /**
         * The names of the preferences of how a map looks and is added to, which a map file keeps. Whether a
         * removal is asked about and the emoji tones are the app's way of working, whatever the map, and the map
         * keeps its theme of its own.
         */
        val MAP_PREFERENCES = setOf(
            ARROW_LINK_COLORS
            , ATTRIBUTE_DISPLAY
            , BACKGROUND_COLOR
            , NEW_NODE_SHAPE
            , NODE_COLORS
            , NODE_FONT
            , NOTE_DISPLAY
            , TEXT_COLORS
        )
    }
}

/**
 * Returns the preferences [read] returns the values of, by their names. A value that is missing, or that is no
 * longer known, as one of a later version of the app may be, is the one in [defaults].
 */
internal fun readUserPreferences(
    read: (String) -> String?
    , defaults: UserPreferences
): UserPreferences = with(UserPreferencesStore) {
    UserPreferences(
        arrowLinkColors = read(ARROW_LINK_COLORS)?.let(::parseColorList)
            ?: defaults.arrowLinkColors
        , attributeDisplay = AttributeDisplay.entries.firstOrNull { it.name == read(ATTRIBUTE_DISPLAY) }
            ?: defaults.attributeDisplay
        // No color is kept as none, for the map to be drawn on its theme's background.
        , backgroundColor = read(BACKGROUND_COLOR).let { text ->
            if (text?.isEmpty() == true) null else text?.let(::parseHexColor) ?: defaults.backgroundColor
        }
        , emojiTones = read(EMOJI_TONES)?.let(::parseEmojiTones)
            ?: defaults.emojiTones
        , isRemoveConfirmed = read(IS_REMOVE_CONFIRMED)?.toBooleanStrictOrNull()
            ?: defaults.isRemoveConfirmed
        , mapThemeId = read(MAP_THEME)
            ?: defaults.mapThemeId
        , newNodeShape = NodeShape.entries.firstOrNull { it.name == read(NEW_NODE_SHAPE) }
            ?: defaults.newNodeShape
        , nodeColors = read(NODE_COLORS)?.let(::parseColorList)
            ?: defaults.nodeColors
        , nodeFont = NodeFont.entries.firstOrNull { it.name == read(NODE_FONT) }
            ?: defaults.nodeFont
        , noteDisplay = NoteDisplay.entries.firstOrNull { it.name == read(NOTE_DISPLAY) }
            ?: defaults.noteDisplay
        , textColors = read(TEXT_COLORS)?.let(::parseColorList)
            ?: defaults.textColors
    )
}

/** Returns the value of each of the preferences, by its name, as [readUserPreferences] reads them. */
internal fun UserPreferences.values(): Map<String, String> = with(UserPreferencesStore) {
    mapOf(
        NEW_NODE_SHAPE to newNodeShape.name
        , IS_REMOVE_CONFIRMED to isRemoveConfirmed.toString()
        , NODE_COLORS to nodeColors.joinToString(",") { it.toHex() }
        , TEXT_COLORS to textColors.joinToString(",") { it.toHex() }
        , ARROW_LINK_COLORS to arrowLinkColors.joinToString(",") { it.toHex() }
        , EMOJI_TONES to emojiTones.entries.joinToString(",") { "${it.key}=${it.value}" }
        , NODE_FONT to nodeFont.name
        , NOTE_DISPLAY to noteDisplay.name
        , ATTRIBUTE_DISPLAY to attributeDisplay.name
        , MAP_THEME to mapThemeId
        , BACKGROUND_COLOR to backgroundColor?.toHex().orEmpty()
    )
}

/** Returns the values of the preferences a map file keeps, by their names. */
internal fun UserPreferences.mapValues(): Map<String, String> =
    values().filterKeys { it in UserPreferencesStore.MAP_PREFERENCES }

/**
 * Returns these preferences with those a map file keeps taken from [values], by their names. The others, and
 * any that is missing or not known, stay as they are.
 */
internal fun UserPreferences.withMapValues(
    values: Map<String, String>
): UserPreferences = readUserPreferences(
    read = { name -> values[name]?.takeIf { name in UserPreferencesStore.MAP_PREFERENCES } }
    , defaults = this
)

private val HEX_COLOR = Regex("#?([0-9A-Fa-f]{6})")

/** The color as `#RRGGBB`. A node is filled with an opaque color, so it has no alpha. */
fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** The opaque color written as `#RRGGBB` or `RRGGBB`, or null when [text] is not one. */
fun parseHexColor(
    text: String
): Color? = HEX_COLOR.matchEntire(text.trim())
    ?.let { Color(0xFF000000 or it.groupValues[1].toLong(16)) }

// A pair that is not an emoji with that skin tone, as a later version of the app may have kept, is dropped.
private fun parseEmojiTones(
    text: String
): Map<String, String> = text.split(",")
    .map { it.split("=") }
    .filter { it.size == 2 && it[1] in EmojiCatalog.find(it[0])?.tones.orEmpty() }
    .associate { it[0] to it[1] }

// A list with a color that is not known, or with no colors at all, is not one the user chose.
private fun parseColorList(
    text: String
): List<Color>? = text.split(",")
    .map { parseHexColor(it) ?: return null }
    .takeIf { it.isNotEmpty() }
