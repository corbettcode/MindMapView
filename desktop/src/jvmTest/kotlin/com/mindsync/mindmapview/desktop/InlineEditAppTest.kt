package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InlineEditAppTest {
    // The window takes every key whatever has the focus. Here the keys go to what has it, so the box that stands
    // for the window takes it back whenever no text field has it.
    private val focusRequester = FocusRequester()

    // The app with its keys taken as the window takes them, and its root selected unless [isRootSelected] is false.
    private fun ComposeUiTest.shownApp(isRootSelected: Boolean = true): MindMapApp {
        lateinit var app: MindMapApp
        setContent {
            val scope = rememberCoroutineScope()
            app = remember { MindMapApp(TestPlatform(), MindMapState(), scope) }
            Box(
                modifier = Modifier
                    .size(1000.dp, 700.dp)
                    .onPreviewKeyEvent(app::onPreviewKeyEvent)
                    .onKeyEvent(app::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable()
            ) {
                app.Content()
            }
        }
        waitForIdle()
        runOnIdle { focusRequester.requestFocus() }
        if (isRootSelected) app.state.select(app.state.tree.getRootNode())
        waitForIdle()
        return app
    }

    private fun ComposeUiTest.press(
        key: Key
        , modifier: Key? = null
    ) {
        if (onAllNodesWithTag(NODE_TEXT_EDITOR_TAG).fetchSemanticsNodes().isEmpty()) {
            runOnIdle { focusRequester.requestFocus() }
        }
        onRoot().performKeyInput {
            if (modifier == null) pressKey(key) else withKeyDown(modifier) { pressKey(key) }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.type(text: String) {
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput(text)
        waitForIdle()
    }

    private fun MindMapState.textOf(id: String) = RichText.parse(tree.getNode(id).description).text

    private fun MindMapState.childTexts(id: String) = tree.getNode(id).children.map { textOf(it) }

    @Test
    fun aMapIsTypedWithTabAndEnter() = runComposeUiTest {
        val app = shownApp()
        val state = app.state
        val rootId = state.tree.getRootNode().id

        press(Key.Tab)
        val first = assertNotNull(state.textEditNodeId)
        assertEquals("Topic 1", state.textOf(first))
        type("Ideas")
        press(Key.Enter)
        assertNull(state.textEditNodeId)

        press(Key.Enter)
        type("Plans")
        press(Key.Tab)
        type("Step one")
        press(Key.Enter)

        assertEquals(listOf("Ideas", "Plans"), state.childTexts(rootId))
        val plans = state.tree.getNode(rootId).children[1]
        assertEquals(listOf("Step one"), state.childTexts(plans))
    }

    // As the app starts, no node is selected, and Tab would take the focus to the menus.
    @Test
    fun tabWithNoNodeSelectedAddsAChildOfTheRoot() = runComposeUiTest {
        val app = shownApp(isRootSelected = false)
        val state = app.state
        val rootId = state.tree.getRootNode().id

        press(Key.Tab)
        type("Ideas")
        press(Key.Enter)

        assertEquals(listOf("Ideas"), state.childTexts(rootId))
    }

    @Test
    fun shiftTabWhileTypingKeepsTheTyping() = runComposeUiTest {
        val app = shownApp()
        press(Key.Tab)
        val id = app.state.textEditNodeId

        press(Key.Tab, modifier = Key.ShiftLeft)

        assertEquals(id, app.state.textEditNodeId)
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).assertIsFocused()
    }

    @Test
    fun shiftEnterAddsASiblingAbove() = runComposeUiTest {
        val app = shownApp()
        val state = app.state
        val rootId = state.tree.getRootNode().id
        press(Key.Tab)
        type("Second")
        press(Key.Enter)

        press(Key.Enter, modifier = Key.ShiftLeft)
        type("First")
        press(Key.Enter)

        assertEquals(listOf("First", "Second"), state.childTexts(rootId))
    }

    @Test
    fun escapeTakesAwayTheNewNodeAndItsNumber() = runComposeUiTest {
        val app = shownApp()
        val state = app.state
        val rootId = state.tree.getRootNode().id

        press(Key.Tab)
        press(Key.Escape)

        assertTrue(state.tree.getNode(rootId).children.isEmpty())
        assertEquals(rootId, state.selectedNode?.id)
        press(Key.Tab)
        assertEquals("Topic 1", state.textOf(state.textEditNodeId!!))
    }

    @Test
    fun f2TypesIntoTheSelectedNodeAndUndoTakesItBack() = runComposeUiTest {
        val app = shownApp()
        val state = app.state
        press(Key.Tab)
        type("Ideas")
        press(Key.Enter)
        val id = state.selectedNode!!.id

        press(Key.F2)
        assertEquals(id, state.textEditNodeId)
        type("Goals")
        press(Key.Enter)
        assertEquals("Goals", state.textOf(id))

        press(Key.Z, modifier = Key.CtrlLeft)
        assertEquals("Ideas", state.textOf(id))
    }

    @Test
    fun whileTypingTheMapsKeysGoToTheText() = runComposeUiTest {
        val app = shownApp()
        val state = app.state
        press(Key.Tab)
        val id = state.textEditNodeId!!

        // Space would fold the node and the arrows would select another one.
        press(Key.Spacebar)
        press(Key.DirectionLeft)

        assertEquals(id, state.textEditNodeId)
        assertEquals(id, state.selectedNode?.id)
    }

    @Test
    fun altEnterOpensTheEditDialog() = runComposeUiTest {
        val app = shownApp()

        press(Key.Enter, modifier = Key.AltLeft)

        assertTrue(app.isDialogOpen)
        assertNull(app.state.textEditNodeId)
    }
}
