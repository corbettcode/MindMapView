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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SummaryAppTest {
    // The window takes every key whatever has the focus. Here the keys go to what has it, so the box that stands
    // for the window takes it back whenever no text field has it.
    private val focusRequester = FocusRequester()

    // The app with its keys taken as the window takes them, and three topics below its root.
    private fun ComposeUiTest.shownApp(): MindMapApp {
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
        val state = app.state
        state.select(state.tree.getRootNode())
        listOf("One", "Two", "Three").forEach { state.addNode(it) }
        state.animateTreeChange()
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

    private fun MindMapState.idOf(text: String): String =
        tree.nodes.values.single { RichText.parse(it.description).text == text }.id

    private fun MindMapState.selectTopics(vararg texts: String) {
        select(tree.getNode(idOf(texts.first())))
        texts.drop(1).forEach { toggleSelected(idOf(it)) }
    }

    private fun ComposeUiTest.type(text: String) {
        onNodeWithTag(NODE_TEXT_EDITOR_TAG).performTextInput(text)
        waitForIdle()
    }

    private fun MindMapState.textOf(id: String) = RichText.parse(tree.getNode(id).description).text

    @Test
    fun ctrlBracketAddsASummaryToTypeIn() = runComposeUiTest {
        val state = shownApp().state
        val rootId = state.tree.getRootNode().id
        state.selectTopics("One", "Two")

        press(Key.RightBracket, modifier = Key.CtrlLeft)
        val summaryId = state.textEditNodeId!!
        assertEquals("Summary", state.textOf(summaryId))
        type("Numbers")
        press(Key.Enter)

        val children = state.tree.getNode(rootId).children.map { state.textOf(it) }
        assertEquals(listOf("One", "Two", "Numbers", "Three"), children)
        assertEquals(listOf(state.idOf("One"), state.idOf("Two")), state.tree.summaryChildIds(summaryId))
        press(Key.Z, modifier = Key.CtrlLeft)
        assertTrue(state.tree.summaries.isEmpty())
        assertEquals(listOf("One", "Two", "Three"), state.tree.getNode(rootId).children.map { state.textOf(it) })
    }

    @Test
    fun escapeTakesTheNewSummaryAway() = runComposeUiTest {
        val state = shownApp().state
        val rootId = state.tree.getRootNode().id
        state.selectTopics("Two")

        press(Key.RightBracket, modifier = Key.CtrlLeft)
        press(Key.Escape)

        assertTrue(state.tree.summaries.isEmpty())
        assertEquals(listOf("One", "Two", "Three"), state.tree.getNode(rootId).children.map { state.textOf(it) })
    }

    @Test
    fun theRootGetsNoSummary() = runComposeUiTest {
        val state = shownApp().state
        state.select(state.tree.getRootNode())

        press(Key.RightBracket, modifier = Key.CtrlLeft)

        assertEquals(null, state.textEditNodeId)
        assertTrue(state.tree.summaries.isEmpty())
    }

    @Test
    fun editMenuAddsASummary() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("Three")

        onNodeWithText("Edit").performClick()
        waitForIdle()
        // The menu is longer than the window here, so the item is scrolled to first.
        onNodeWithText("Add Summary").performScrollTo().performClick()
        waitForIdle()
        press(Key.Enter)

        assertEquals(listOf(state.idOf("Three")), state.tree.summaries.values.single())
    }
}
