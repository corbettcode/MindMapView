package com.corbettcode.mindmapview.library

import androidx.compose.ui.graphics.Color
import com.corbettcode.mindmapview.library.data.NodeBorder
import com.corbettcode.mindmapview.library.data.NodeBorderStyle
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.model.AttributeDisplay
import com.corbettcode.mindmapview.library.model.LevelStyle
import com.corbettcode.mindmapview.library.model.MapThemes
import com.corbettcode.mindmapview.library.model.NoteDisplay
import com.corbettcode.mindmapview.library.model.TopicKind
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class TopicStyleTest {
    private val topicStyle = LevelStyle(
        fill = Color(0xFF112233)
        , text = Color(0xFFFFFFFF)
        , border = NodeBorder(NodeBorderStyle.LINE)
    )

    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        theme = MapThemes.byId("ocean")
        addNode("a", getRootNode().id, "A")
        addNode("b", "a", "B")
    }

    @Test
    fun topicStyleTakesThePlaceOfTheThemesForItsLevel() {
        val tree = tree().apply { topicStyles = mapOf(TopicKind.TOPIC to topicStyle) }

        assertEquals(topicStyle.fill, tree.fillOf("a"))
        assertEquals(topicStyle.text, tree.textColorOf("a"))
        assertEquals(topicStyle.border, tree.drawnBorderOf("a"))
        // A kind without a style of its own is drawn as the theme draws it.
        assertEquals(tree.theme.subtopic.fill, tree.fillOf("b"))
    }

    @Test
    fun nodeWithAColorOfItsOwnKeepsIt() {
        val tree = tree().apply { topicStyles = mapOf(TopicKind.TOPIC to topicStyle) }
        tree.updateNodeColor("a", Color(0xFF00FF00))

        assertEquals(Color(0xFF00FF00), tree.fillOf("a"))
    }

    @Test
    fun topicStylesAreKeptForAMapThatIsOpenedAfterThem() {
        val state = MindMapState(tree())
        state.topicStyles = mapOf(TopicKind.SUBTOPIC to topicStyle)

        state.setTree(tree())

        assertEquals(topicStyle.fill, state.tree.fillOf("b"))
    }

    @Test
    fun noteAndAttributesAreShownAsTheirKindOfTopicIsSetTo() {
        val tree = tree().apply {
            topicNoteDisplays = mapOf(TopicKind.TOPIC to NoteDisplay.INLINE)
            topicAttributeDisplays = mapOf(TopicKind.SUBTOPIC to AttributeDisplay.HIDDEN)
        }

        assertEquals(NoteDisplay.INLINE, tree.shownNoteDisplayOf("a", NoteDisplay.FLYOVER))
        assertEquals(NoteDisplay.FLYOVER, tree.shownNoteDisplayOf("b", NoteDisplay.FLYOVER))
        assertEquals(AttributeDisplay.HIDDEN, tree.shownAttributeDisplayOf("b", AttributeDisplay.INLINE))
        // A node's own way wins over that of its kind.
        tree.setNoteDisplay("a", NoteDisplay.HIDDEN)
        assertEquals(NoteDisplay.HIDDEN, tree.shownNoteDisplayOf("a", NoteDisplay.FLYOVER))
    }
}
