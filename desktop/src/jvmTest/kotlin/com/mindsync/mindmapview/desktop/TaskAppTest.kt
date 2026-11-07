package com.mindsync.mindmapview.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.mindsync.library.MindMapState
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.NODE_TEXT_EDITOR_TAG
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskAppTest {
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

    private fun MindMapState.taskOf(text: String) = tree.taskOf(idOf(text))

    @Test
    fun ctrlDigitGivesTheSelectedTopicsThatPriority() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("One", "Two")

        press(Key.Two, modifier = Key.CtrlLeft)

        assertEquals(listOf(2, 2, null), listOf("One", "Two", "Three").map { state.taskOf(it).priority })
        press(Key.Z, modifier = Key.CtrlLeft)
        assertEquals(listOf(null, null), listOf("One", "Two").map { state.taskOf(it).priority })
    }

    @Test
    fun ctrlZeroTakesThePriorityAway() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("One")
        press(Key.NumPad7, modifier = Key.CtrlLeft)
        assertEquals(7, state.taskOf("One").priority)

        press(Key.Zero, modifier = Key.CtrlLeft)

        assertNull(state.taskOf("One").priority)
    }

    @Test
    fun ctrlDTogglesDone() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("Three")

        press(Key.D, modifier = Key.CtrlLeft)
        assertEquals(TaskProgress.DONE, state.taskOf("Three").progress)
        press(Key.D, modifier = Key.CtrlLeft)
        assertEquals(TaskProgress.NOT_STARTED, state.taskOf("Three").progress)
    }

    @Test
    fun whileTypingCtrlDigitGoesToTheText() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("Two")
        press(Key.F2)

        press(Key.Five, modifier = Key.CtrlLeft)
        press(Key.Enter)

        assertNull(state.taskOf("Two").priority)
    }

    @Test
    fun editMenuGivesAPriority() = runComposeUiTest {
        val state = shownApp().state
        state.selectTopics("One")

        onNodeWithText("Edit").performClick()
        waitForIdle()
        // The menu is longer than the window here, so the item is scrolled to first.
        onNodeWithText("Task").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Priority 3").performScrollTo().performClick()
        waitForIdle()

        assertEquals(3, state.taskOf("One").priority)
    }

    // The edit dialog is a window of its own, which the test cannot reach, so its Task section is checked by eye.
    @Test
    fun nodeMenuTogglesDone() = runComposeUiTest {
        val state = shownApp().state
        val node = state.tree.getNode(state.idOf("Two"))
        val center = with(density) {
            Offset(node.path.centerX.dpVal.dp.toPx(), node.path.centerY.dpVal.dp.toPx())
        } * state.scaleFactor + state.currentPoint

        onNodeWithTag(MIND_MAP_TAG).performMouseInput { rightClick(center) }
        waitForIdle()
        onNodeWithText("Task").performClick()
        waitForIdle()
        onNodeWithText("Toggle Done").performScrollTo().performClick()
        waitForIdle()

        assertEquals(TaskProgress.DONE, state.taskOf("Two").progress)
    }
}
