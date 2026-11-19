package com.mindsync.library.outline

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMap
import com.mindsync.library.MindMapState
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.TextEditHost
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OutlineTypingTest {
    // The root with "p" on its right; "b" and "c" below "p". The map and the outline are shown side by side.
    private fun ComposeUiTest.shownState(): MindMapState {
        val tree = Tree<Any>(Dp(300f), Dp(300f)).apply {
            addNode("p", getRootNode().id, "P")
            addNode("b", "p", "B")
            addNode("c", "p", "C")
        }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent {
            Row {
                MindMapOutline(state = state, modifier = Modifier.size(300.dp, 400.dp))
                MindMap(state = state, modifier = Modifier.size(600.dp, 400.dp))
            }
        }
        waitForIdle()
        return state
    }

    private fun MindMapState.textOf(id: String) = RichText.parse(tree.getNode(id).description).text

    private fun ComposeUiTest.doubleClick(id: String) {
        onNodeWithTag(outlineRowTag(id)).performMouseInput {
            click()
            click()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.press(
        key: Key
        , held: List<Key> = emptyList()
    ) {
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performKeyInput {
            fun press(index: Int) {
                if (index == held.size) pressKey(key) else withKeyDown(held[index]) { press(index + 1) }
            }
            press(0)
        }
        waitForIdle()
    }

    @Test
    fun doubleClickTypesInTheRowNotOnTheMap() = runComposeUiTest {
        val state = shownState()

        doubleClick("b")

        assertEquals("b", state.textEditNodeId)
        assertEquals(TextEditHost.OUTLINE, state.textEditHost)
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).assertExists()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).assertDoesNotExist()
    }

    @Test
    fun typedTextShowsOnTheMapAndIsOneChange() = runComposeUiTest {
        val state = shownState()
        doubleClick("b")

        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performTextReplacement("Bananas")
        waitForIdle()
        assertEquals("Bananas", state.textOf("b"))

        press(Key.Escape)
        assertEquals("B", state.textOf("b"))
        assertFalse(state.canUndo)
    }

    @Test
    fun enterGoesOnInANewSiblingBelow() = runComposeUiTest {
        val state = shownState()
        doubleClick("b")
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performTextReplacement("Bananas")

        press(Key.Enter)
        val added = state.tree.getNode("p").children[1]
        assertEquals(added, state.textEditNodeId)
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performTextInput("Berries")
        waitForIdle()
        press(Key.Enter)
        press(Key.Enter)

        assertEquals(listOf("b", added, "c"), state.tree.getNode("p").children)
        assertEquals("Berries", state.textOf(added))
        assertNull(state.textEditNodeId, "Enter in an empty new topic ends the typing")
        state.undo()
        state.undo()
        assertEquals(listOf("b", "c"), state.tree.getNode("p").children)
        assertEquals("B", state.textOf("b"))
    }

    @Test
    fun tabIndentsAndShiftTabOutdentsWhileTyping() = runComposeUiTest {
        val state = shownState()
        doubleClick("c")
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performTextReplacement("Cherries")

        press(Key.Tab)
        assertEquals("b", state.tree.getNode("c").parentId)
        assertEquals("c", state.textEditNodeId)

        press(Key.Tab, listOf(Key.ShiftLeft))
        press(Key.Tab, listOf(Key.ShiftLeft))
        assertEquals(state.tree.getRootNode().id, state.tree.getNode("c").parentId)
        assertEquals("c", state.textEditNodeId)

        press(Key.Enter)
        state.select(null)
        state.undo()
        waitForIdle()
        assertEquals("p", state.tree.getNode("c").parentId, "the moves and the text are one change")
        assertEquals("C", state.textOf("c"))
    }

    @Test
    fun altShiftUpMovesTheTopicWhileTyping() = runComposeUiTest {
        val state = shownState()
        doubleClick("c")

        press(Key.DirectionUp, listOf(Key.AltLeft, Key.ShiftLeft))

        assertEquals(listOf("c", "b"), state.tree.getNode("p").children)
        assertEquals("c", state.textEditNodeId)
    }

    @Test
    fun backspaceInAnEmptyTopicRemovesItAndTypesOnAbove() = runComposeUiTest {
        val state = shownState()
        doubleClick("c")
        onNodeWithTag(OUTLINE_TEXT_EDITOR_TAG).performTextReplacement("")
        waitForIdle()

        press(Key.Backspace)

        assertEquals(listOf("b"), state.tree.getNode("p").children)
        assertEquals("b", state.textEditNodeId)
        press(Key.Escape)
        state.undo()
        assertEquals(listOf("b", "c"), state.tree.getNode("p").children)
        assertEquals("C", state.textOf("c"))
        assertTrue(!state.canUndo)
    }
}
