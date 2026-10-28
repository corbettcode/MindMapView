package com.mindsync.mindmapview.desktop

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.Tree
import com.mindsync.library.emoji.EmojiCatalog
import com.mindsync.mindmapview.desktop.component.GROUP_ICONS
import com.mindsync.mindmapview.desktop.component.emojisInMap
import com.mindsync.mindmapview.desktop.component.shown
import com.mindsync.mindmapview.desktop.component.startGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiPickerModelTest {
    @Test
    fun mapEmojisAreLeadingOnesInOrderWithoutRepeats() {
        val tree = Tree<Any>()
        val rootId = tree.getRootNode().id
        tree.addNode("a", rootId, "<p>⭐ a</p>", NodeShape.OVAL, Color.Red, Color.White)
        tree.addNode("b", rootId, "<p>b 🚀</p>", NodeShape.OVAL, Color.Red, Color.White)
        tree.addNode("c", rootId, "<p>⭐ c</p>", NodeShape.OVAL, Color.Red, Color.White)
        tree.addNode("d", rootId, "<p>👍🏽 d</p>", NodeShape.OVAL, Color.Red, Color.White)
        assertEquals(listOf("🌱", "⭐", "👍🏽"), emojisInMap(tree))
    }

    @Test
    fun rememberedToneIsShown() {
        val thumbs = EmojiCatalog.find("👍")!!
        assertEquals("👍🏽", thumbs.shown(mapOf("👍" to "👍🏽")))
        assertEquals("👍", thumbs.shown(emptyMap()))
    }

    @Test
    fun pickerStartsOnTheSelectedEmojisGroup() {
        assertEquals("People & Body", startGroup("👋🏿").name)
        assertEquals("Smileys & Emotion", startGroup(null).name)
    }

    @Test
    fun everyGroupHasAnIcon() = assertEquals(EmojiCatalog.groups.map { it.name }, GROUP_ICONS.keys.toList())
}
