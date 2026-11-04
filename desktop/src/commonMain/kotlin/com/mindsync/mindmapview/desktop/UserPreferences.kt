package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.NodeShape
import com.mindsync.library.emoji.EmojiCatalog
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.LevelStyle
import com.mindsync.library.model.MapTheme
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.NoteDisplay
import com.mindsync.library.model.TopicKind
import com.mindsync.mindmapview.desktop.dialog.TopicPreferences
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
    // Preferences kept before there were topic styles take those of the theme new maps start in.
    var preferences: UserPreferences =
        MapThemes.byId(read(MAP_THEME)).let { theme ->
            readUserPreferences(read, UserPreferences().withTheme(theme), theme)
        }
        private set

    fun update(newPreferences: UserPreferences): UserPreferences {
        preferences = newPreferences
        newPreferences.values().forEach { (name, value) -> write(name, value) }
        return preferences
    }

    companion object {
        const val IS_REMOVE_CONFIRMED = "isRemoveConfirmed"
        const val ARROW_LINK_COLORS = "arrowLinkColors"
        const val EMOJI_TONES = "emojiTones"
        const val NODE_FONT = "nodeFont"
        const val NOTE_DISPLAY = "noteDisplay"
        const val ATTRIBUTE_DISPLAY = "attributeDisplay"
        const val MAP_THEME = "mapTheme"
        const val BACKGROUND_COLOR = "backgroundColor"

        // One shape and lists of colors for every kind of topic, as kept before each kind had its own. They are
        // still read, for the kinds that have none of their own.
        const val NEW_NODE_SHAPE = "newNodeShape"
        const val NODE_COLORS = "nodeColors"
        const val TEXT_COLORS = "textColors"

        /** The name a preference of the topics of [kind] is kept under, as `topic.fill`. */
        fun topicKey(
            kind: TopicKind
            , name: String
        ): String = "${topicPrefix(kind)}.$name"

        const val SHAPE = "shape"
        const val FILL = "fill"
        const val TEXT = "text"
        const val BORDER = "border"
        const val FILL_COLORS = "fillColors"
        const val TOPIC_TEXT_COLORS = "textColors"

        private fun topicPrefix(
            kind: TopicKind
        ): String = when (kind) {
            TopicKind.ROOT_TOPIC -> "rootTopic"
            TopicKind.TOPIC -> "topic"
            TopicKind.SUBTOPIC -> "subtopic"
        }

        /**
         * The names of the preferences of how a map looks and is added to, which a map file keeps. Whether a
         * removal is asked about and the emoji tones are the app's way of working, whatever the map, and the map
         * keeps its theme of its own.
         */
        val MAP_PREFERENCES: Set<String> = setOf(
            ARROW_LINK_COLORS
            , ATTRIBUTE_DISPLAY
            , BACKGROUND_COLOR
            , NODE_FONT
            , NOTE_DISPLAY
        ) + TopicKind.entries.flatMap { kind ->
            listOf(SHAPE, FILL, TEXT, BORDER, FILL_COLORS, TOPIC_TEXT_COLORS).map { topicKey(kind, it) }
        }

        // What a map file saved before each kind of topic had its own preferences keeps, read for all of them.
        internal val LEGACY_MAP_PREFERENCES = setOf(NEW_NODE_SHAPE, NODE_COLORS, TEXT_COLORS)
    }
}

/**
 * Returns the preferences [read] returns the values of, by their names. A value that is missing, or that is no
 * longer known, as one of a later version of the app may be, is the one in [defaults]. The one shape kept for
 * every kind of topic before each had its own is not taken for a kind [theme] has a shape of its own for.
 */
internal fun readUserPreferences(
    read: (String) -> String?
    , defaults: UserPreferences
    , theme: MapTheme? = null
): UserPreferences = with(UserPreferencesStore) {
    val topics = TopicKind.entries.associateWith { kind ->
        readTopic(read, kind, defaults.topicOf(kind), isThemeShaped = theme?.newNodeShapeOf(kind) != null)
    }
    UserPreferences(
        arrowLinkColors = read(ARROW_LINK_COLORS)?.let(::parseColorList)
            ?: defaults.arrowLinkColors
        , attributeDisplay = AttributeDisplay.entries.firstOrNull { it.name == read(ATTRIBUTE_DISPLAY) }
            ?: defaults.attributeDisplay
        // No color is kept as none, for the map to be drawn on its theme's background.
        , backgroundColor = readOptionalColor(read(BACKGROUND_COLOR), defaults.backgroundColor)
        , emojiTones = read(EMOJI_TONES)?.let(::parseEmojiTones)
            ?: defaults.emojiTones
        , isRemoveConfirmed = read(IS_REMOVE_CONFIRMED)?.toBooleanStrictOrNull()
            ?: defaults.isRemoveConfirmed
        , mapThemeId = read(MAP_THEME)
            ?: defaults.mapThemeId
        , nodeFont = NodeFont.entries.firstOrNull { it.name == read(NODE_FONT) }
            ?: defaults.nodeFont
        , noteDisplay = NoteDisplay.entries.firstOrNull { it.name == read(NOTE_DISPLAY) }
            ?: defaults.noteDisplay
        , rootTopic = topics.getValue(TopicKind.ROOT_TOPIC)
        , subtopic = topics.getValue(TopicKind.SUBTOPIC)
        , topic = topics.getValue(TopicKind.TOPIC)
    )
}

// A kind of topic without a shape or colors of its own takes the one shape and lists kept for all of them before,
// but for a shape the theme has for the kind, [isThemeShaped], which is kept: the one shape was the theme's
// for all kinds, or one chosen before the theme had a shape for each.
private fun readTopic(
    read: (String) -> String?
    , kind: TopicKind
    , defaults: TopicPreferences
    , isThemeShaped: Boolean
): TopicPreferences = with(UserPreferencesStore) {
    val key = { name: String -> topicKey(kind, name) }
    val shapeName = read(key(SHAPE)) ?: read(NEW_NODE_SHAPE).takeUnless { isThemeShaped }
    TopicPreferences(
        shape = NodeShape.entries.firstOrNull { it.name == shapeName }
            ?: defaults.shape
        , style = LevelStyle(
            fill = readOptionalColor(read(key(FILL)), defaults.style.fill)
            , text = readOptionalColor(read(key(TEXT)), defaults.style.text)
            , border = read(key(BORDER))?.let(::parseBorder) ?: defaults.style.border
        )
        , fillColors = (read(key(FILL_COLORS)) ?: read(NODE_COLORS))?.let(::parseColorList)
            ?: defaults.fillColors
        , textColors = (read(key(TOPIC_TEXT_COLORS)) ?: read(TEXT_COLORS))?.let(::parseColorList)
            ?: defaults.textColors
    )
}

/** Returns the value of each of the preferences, by its name, as [readUserPreferences] reads them. */
internal fun UserPreferences.values(): Map<String, String> = with(UserPreferencesStore) {
    mapOf(
        IS_REMOVE_CONFIRMED to isRemoveConfirmed.toString()
        , ARROW_LINK_COLORS to arrowLinkColors.joinToString(",") { it.toHex() }
        , EMOJI_TONES to emojiTones.entries.joinToString(",") { "${it.key}=${it.value}" }
        , NODE_FONT to nodeFont.name
        , NOTE_DISPLAY to noteDisplay.name
        , ATTRIBUTE_DISPLAY to attributeDisplay.name
        , MAP_THEME to mapThemeId
        , BACKGROUND_COLOR to backgroundColor?.toHex().orEmpty()
    ) + TopicKind.entries.flatMap { kind ->
        val topic = topicOf(kind)
        listOf(
            topicKey(kind, SHAPE) to topic.shape.name
            , topicKey(kind, FILL) to topic.style.fill?.toHex().orEmpty()
            , topicKey(kind, TEXT) to topic.style.text?.toHex().orEmpty()
            , topicKey(kind, BORDER) to topic.style.border.toText()
            , topicKey(kind, FILL_COLORS) to topic.fillColors.joinToString(",") { it.toHex() }
            , topicKey(kind, TOPIC_TEXT_COLORS) to topic.textColors.joinToString(",") { it.toHex() }
        )
    }
}

/** Returns the values of the preferences a map file keeps, by their names. */
internal fun UserPreferences.mapValues(): Map<String, String> =
    values().filterKeys { it in UserPreferencesStore.MAP_PREFERENCES }

/**
 * Returns these preferences with those a map file keeps taken from [values], by their names, for a map in
 * [theme]. The others, and any that is missing or not known, stay as they are.
 */
internal fun UserPreferences.withMapValues(
    values: Map<String, String>
    , theme: MapTheme? = null
): UserPreferences = readUserPreferences(
    read = { name ->
        values[name]?.takeIf {
            name in UserPreferencesStore.MAP_PREFERENCES || name in UserPreferencesStore.LEGACY_MAP_PREFERENCES
        }
    }
    , defaults = this
    , theme = theme
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

// An empty value is kept for no color, and one that is not known is the default.
private fun readOptionalColor(
    text: String?
    , default: Color?
): Color? = when {
    text == null -> default
    text.isEmpty() -> null
    else -> parseHexColor(text) ?: default
}

// A border is kept as its style, width and color, as `LINE,TWO,#000000`.
private fun NodeBorder.toText(): String = "${style.name},${width.name},${color.toHex()}"

private fun parseBorder(
    text: String
): NodeBorder? {
    val parts = text.split(",")
    if (parts.size != 3) return null
    return NodeBorder(
        style = NodeBorderStyle.entries.firstOrNull { it.name == parts[0] } ?: return null
        , width = NodeLineWidth.entries.firstOrNull { it.name == parts[1] } ?: return null
        , color = parseHexColor(parts[2]) ?: return null
    )
}
