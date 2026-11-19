package com.corbettcode.mindmapview.library

import androidx.compose.ui.graphics.Color
import com.corbettcode.mindmapview.library.data.ArrowLink
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NewArrowLinkColorTest {
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            addNode("a", getRootNode().id, "A")
            addNode("b", getRootNode().id, "B")
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    @Test
    fun newArrowLinkIsInTheDefaultColorUntilAnotherIsSet() {
        assertEquals(ArrowLink.DEFAULT_COLOR, state().addArrowLink("a", "b")?.color)
    }

    @Test
    fun newArrowLinkIsInTheColorSetForOne() {
        val state = state().apply { newArrowLinkColor = Color(0xFF0B4F6C) }

        assertEquals(Color(0xFF0B4F6C), state.addArrowLink("a", "b")?.color)
    }
}
