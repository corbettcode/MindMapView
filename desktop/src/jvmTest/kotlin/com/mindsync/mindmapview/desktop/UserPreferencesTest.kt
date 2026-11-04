package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeBorder
import com.mindsync.library.data.NodeBorderStyle
import com.mindsync.library.data.NodeLineWidth
import com.mindsync.library.data.NodeShape
import com.mindsync.library.model.AttributeDisplay
import com.mindsync.library.model.MapThemes
import com.mindsync.library.model.LevelStyle
import com.mindsync.library.model.NoteDisplay
import com.mindsync.library.model.TopicKind
import com.mindsync.mindmapview.desktop.dialog.TopicPreferences
import com.mindsync.mindmapview.desktop.dialog.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class UserPreferencesTest {
    private val stored = mutableMapOf<String, String>()

    private fun store() = UserPreferencesStore(read = { stored[it] }, write = { name, value -> stored[name] = value })

    @Test
    fun preferencesThatWereNeverSetAreTheDefaults() {
        assertEquals(UserPreferences(), store().preferences)
    }

    @Test
    fun preferencesAreKeptFromOneRunToTheNext() {
        val changed = UserPreferences(
            arrowLinkColors = listOf(Color(0xFF654321), Color.Black)
            , backgroundColor = Color(0xFF101820)
            , isRemoveConfirmed = false
            , subtopic = TopicPreferences(
                shape = NodeShape.entries.last()
                , style = LevelStyle(
                    fill = Color(0xFF445566)
                    , text = null
                    , border = NodeBorder(NodeBorderStyle.LINE, NodeLineWidth.FOUR, Color(0xFF778899))
                )
                , fillColors = listOf(Color(0xFF123456), Color.White)
                , textColors = listOf(Color(0xFFABCDEF))
            )
            , rootTopic = TopicPreferences(
                attributeDisplay = AttributeDisplay.FLYOVER
                , noteDisplay = NoteDisplay.HIDDEN
                , style = LevelStyle(fill = null, text = Color(0xFF010203))
            )
        )
        assertEquals(changed, store().update(changed))
        assertEquals(changed, store().preferences)
    }

    @Test
    fun valueThatIsNotKnownIsTheDefault() {
        stored[UserPreferencesStore.NEW_NODE_SHAPE] = "STAR"
        stored[UserPreferencesStore.IS_REMOVE_CONFIRMED] = "maybe"
        stored[UserPreferencesStore.NODE_COLORS] = "#123456,teal"
        stored[UserPreferencesStore.TEXT_COLORS] = "#12"
        stored[UserPreferencesStore.ARROW_LINK_COLORS] = "#123456,"
        assertEquals(UserPreferences(), store().preferences)
    }

    @Test
    fun noNodeColorsAreTheDefaultNodeColors() {
        stored[UserPreferencesStore.NODE_COLORS] = ""
        assertEquals(DEFAULT_NODE_COLORS, store().preferences.topic.fillColors)
    }

    @Test
    fun shapeAndColorsKeptBeforeEachTopicHadItsOwnAreThoseOfEveryTopic() {
        stored[UserPreferencesStore.NEW_NODE_SHAPE] = NodeShape.OVAL.name
        stored[UserPreferencesStore.NODE_COLORS] = "#123456"
        stored[UserPreferencesStore.TEXT_COLORS] = "#654321"

        val preferences = store().preferences

        TopicKind.entries.forEach { kind ->
            assertEquals(NodeShape.OVAL, preferences.topicOf(kind).shape)
            assertEquals(listOf(Color(0xFF123456)), preferences.topicOf(kind).fillColors)
            assertEquals(listOf(Color(0xFF654321)), preferences.topicOf(kind).textColors)
        }
    }

    @Test
    fun shapeKeptBeforeEachTopicHadItsOwnGivesWayToTheThemesShapeForAKind() {
        stored[UserPreferencesStore.MAP_THEME] = "contrast"
        stored[UserPreferencesStore.NEW_NODE_SHAPE] = NodeShape.OVAL.name

        val preferences = store().preferences

        assertEquals(NodeShape.RECTANGLE, preferences.topic.shape)
        assertEquals(NodeShape.LINE, preferences.subtopic.shape)
    }

    @Test
    fun preferencesKeptBeforeTopicStylesTakeThoseOfTheirTheme() {
        stored[UserPreferencesStore.MAP_THEME] = "ocean"

        val ocean = MapThemes.byId("ocean")
        assertEquals(ocean.root, store().preferences.rootTopic.style)
        assertEquals(ocean.mainTopic, store().preferences.topic.style)
        assertEquals(ocean.subtopic, store().preferences.subtopic.style)
    }

    @Test
    fun emojiTonesAreKept() {
        store().update(UserPreferences(emojiTones = mapOf("👍" to "👍🏽", "✋" to "✋🏿")))
        assertEquals(mapOf("👍" to "👍🏽", "✋" to "✋🏿"), store().preferences.emojiTones)
    }

    @Test
    fun unknownEmojiTonesAreIgnored() {
        stored[UserPreferencesStore.EMOJI_TONES] = "👍=👍🏽,😀=😀🏽,x=y,garbage"
        assertEquals(mapOf("👍" to "👍🏽"), store().preferences.emojiTones)
    }

    @Test
    fun backgroundColorIsKept() {
        store().update(UserPreferences(backgroundColor = Color(0xFF223344)))
        assertEquals(Color(0xFF223344), store().preferences.backgroundColor)
    }

    @Test
    fun noBackgroundColorIsKeptAsNone() {
        store().update(UserPreferences(backgroundColor = Color(0xFF223344)))
        store().update(UserPreferences())
        assertEquals(null, store().preferences.backgroundColor)
    }

    @Test
    fun eachTopicKeepsItsOwnFont() {
        store().update(
            UserPreferences(
                rootTopic = TopicPreferences(font = NodeFont.LORA)
                , topic = TopicPreferences(font = NodeFont.CAVEAT)
            )
        )

        assertEquals(NodeFont.LORA, store().preferences.rootTopic.font)
        assertEquals(NodeFont.CAVEAT, store().preferences.topic.font)
        assertEquals(NodeFont.DEFAULT, store().preferences.subtopic.font)
    }

    @Test
    fun unknownNodeFontIsDefault() {
        stored[UserPreferencesStore.NODE_FONT] = "COMIC_SANS"
        assertEquals(NodeFont.DEFAULT, store().preferences.topic.font)
    }

    @Test
    fun fontKeptBeforeEachTopicHadItsOwnIsThatOfEveryTopic() {
        stored[UserPreferencesStore.NODE_FONT] = NodeFont.INTER.name

        TopicKind.entries.forEach { assertEquals(NodeFont.INTER, store().preferences.topicOf(it).font) }
    }

    @Test
    fun themesFontWinsOverTheFontKeptBeforeEachTopicHadItsOwn() {
        // Paper's font was drawn before, whatever the one font was set to.
        stored[UserPreferencesStore.MAP_THEME] = "paper"
        stored[UserPreferencesStore.NODE_FONT] = NodeFont.INTER.name

        TopicKind.entries.forEach { assertEquals(NodeFont.LORA, store().preferences.topicOf(it).font) }
    }

    @Test
    fun eachTopicKeepsItsOwnNoteDisplay() {
        store().update(UserPreferences(subtopic = TopicPreferences(noteDisplay = NoteDisplay.INLINE)))
        assertEquals(NoteDisplay.INLINE, store().preferences.subtopic.noteDisplay)
        assertEquals(NoteDisplay.FLYOVER, store().preferences.topic.noteDisplay)
    }

    @Test
    fun unknownNoteDisplayIsFlyover() {
        stored[UserPreferencesStore.NOTE_DISPLAY] = "SIDEBAR"
        assertEquals(NoteDisplay.FLYOVER, store().preferences.topic.noteDisplay)
    }

    @Test
    fun unknownAttributeDisplayIsInline() {
        stored[UserPreferencesStore.ATTRIBUTE_DISPLAY] = "SIDEBAR"
        assertEquals(AttributeDisplay.INLINE, store().preferences.topic.attributeDisplay)
    }

    @Test
    fun notesAndAttributesKeptBeforeEachTopicHadItsOwnAreThoseOfEveryTopic() {
        stored[UserPreferencesStore.NOTE_DISPLAY] = NoteDisplay.INLINE.name
        stored[UserPreferencesStore.ATTRIBUTE_DISPLAY] = AttributeDisplay.HIDDEN.name

        TopicKind.entries.forEach { kind ->
            assertEquals(NoteDisplay.INLINE, store().preferences.topicOf(kind).noteDisplay)
            assertEquals(AttributeDisplay.HIDDEN, store().preferences.topicOf(kind).attributeDisplay)
        }
    }

    @Test
    fun hexColorIsReadWithOrWithoutItsHash() {
        assertEquals(Color(0xFF2D5728), parseHexColor("#2d5728"))
        assertEquals(Color(0xFF2D5728), parseHexColor("2D5728"))
        assertEquals("#2D5728", Color(0xFF2D5728).toHex())
        assertEquals(null, parseHexColor("#2D57"))
    }
}
