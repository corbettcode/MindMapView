package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import com.mindsync.library.util.RichText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NodeTextEditorTest {
    // The root in the middle of the screen with "a" below it.
    private fun ComposeUiTest.shownState(): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply { addNode("a", getRootNode().id, "Apple") }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp).testTag(TAG)) }
        waitForIdle()
        state.animateTreeChange()
        waitForIdle()
        state.select(state.tree.getNode("a"))
        return state
    }

    private fun MindMapState.textOf(id: String) = RichText.parse(tree.getNode(id).description).text

    @Test
    fun typingReplacesTheSelectedTextAndShowsItInTheNode() = runComposeUiTest {
        val state = shownState()

        state.startTextEdit()
        waitForIdle()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).assertIsFocused()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput("Pear")
        waitForIdle()

        assertEquals("Pear", state.textOf("a"))
        assertEquals("a", state.textEditNodeId)
    }

    @Test
    fun typingStartedWithACharacterGoesOnAfterIt() = runComposeUiTest {
        val state = shownState()

        state.startTextEdit("P")
        waitForIdle()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput("ear")
        waitForIdle()

        assertEquals("Pear", state.textOf("a"))
    }

    @Test
    fun enterEndsTheTyping() = runComposeUiTest {
        val state = shownState()
        state.startTextEdit()
        waitForIdle()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput("Pear")

        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertNull(state.textEditNodeId)
        assertEquals("Pear", state.textOf("a"))
        assertTrue(state.canUndo)
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).assertDoesNotExist()
    }

    @Test
    fun escapeCancelsTheTyping() = runComposeUiTest {
        val state = shownState()
        state.startTextEdit()
        waitForIdle()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput("Pear")

        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertNull(state.textEditNodeId)
        assertEquals("Apple", state.textOf("a"))
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).assertDoesNotExist()
    }

    @Test
    fun pressOnTheMapEndsTheTyping() = runComposeUiTest {
        val state = shownState()
        state.startTextEdit()
        waitForIdle()
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput("Pear")

        onNodeWithTag(TAG).performTouchInput { click(Offset(5f, 5f)) }
        waitForIdle()

        assertNull(state.textEditNodeId)
        assertEquals("Pear", state.textOf("a"))
    }

    @Test
    fun ctrlBMakesTheSelectedTextBold() = runComposeUiTest {
        val state = shownState()
        state.startTextEdit()
        waitForIdle()

        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.B) } }
        waitForIdle()

        val description = state.tree.getNode("a").description
        assertTrue("<b>" in description || "bold" in description || "font-weight" in description, description)
    }

    @Test
    fun textFieldIsOverTheNodesTextWhenZoomed() = runComposeUiTest {
        val state = shownState()
        state.onTransform(Offset.Zero, 2f)
        waitForIdle()

        state.startTextEdit()
        waitForIdle()

        val bounds = onNodeWithTag(NODE_TEXT_EDITOR_TAG).getBoundsInRoot()
        val node = state.tree.getNode("a")
        val center = with(density) {
            Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx()) * state.scaleFactor +
                state.currentPoint
        }
        val boundsCenter = with(density) {
            Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
        }
        assertTrue(abs(boundsCenter.x - center.x) < 2f, "$bounds $boundsCenter is not at $center")
        assertTrue(abs(boundsCenter.y - center.y) < 2f, "$boundsCenter is not at $center")
    }
}

private const val TAG = "mindMap"
