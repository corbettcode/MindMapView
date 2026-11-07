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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.model.ProgressFilter
import com.mindsync.library.model.TaskFilter
import com.mindsync.library.util.RichText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FilterBarTest {
    // The window takes every key whatever has the focus. Here the keys go to what has it, so the box that stands
    // for the window takes it back whenever no text field has it.
    private val focusRequester = FocusRequester()

    // The app with its keys taken as the window takes them, and four apples below its root, of which the second
    // and the fourth are priority 1 and the fourth is done.
    private fun ComposeUiTest.shownApp(): MindMapApp {
        lateinit var app: MindMapApp
        setContent {
            val scope = rememberCoroutineScope()
            app = remember { MindMapApp(TestPlatform(), MindMapState(), scope) }
            Box(
                modifier = Modifier
                    .size(1200.dp, 700.dp)
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
        listOf("Apple 1", "Apple 2", "Apple 3", "Apple 4").forEach { state.addNode(it) }
        state.tree.setTask(state.idOf("Apple 2"), NodeTask(priority = 1))
        state.tree.setTask(state.idOf("Apple 4"), NodeTask(priority = 1, progress = TaskProgress.DONE))
        state.animateTreeChange()
        waitForIdle()
        return app
    }

    // The key goes to the window, unless [isToFindBar], when it goes to the find bar's field, which has the focus.
    private fun ComposeUiTest.press(
        key: Key
        , modifiers: List<Key> = emptyList()
        , isToFindBar: Boolean = false
    ) {
        if (!isToFindBar) runOnIdle { focusRequester.requestFocus() }
        onRoot().performKeyInput {
            fun pressWith(rest: List<Key>) {
                if (rest.isEmpty()) pressKey(key) else withKeyDown(rest.first()) { pressWith(rest.drop(1)) }
            }
            pressWith(modifiers)
        }
        waitForIdle()
    }

    private fun MindMapState.idOf(text: String): String =
        tree.nodes.values.single { RichText.parse(it.description).text == text }.id

    private fun MindMapState.selectedText(): String? = selectedNode?.let { RichText.parse(it.description).text }

    @Test
    fun ctrlShiftFOpensTheBarWhoseChipsFilter() = runComposeUiTest {
        val state = shownApp().state

        press(Key.F, listOf(Key.CtrlLeft, Key.ShiftLeft))
        onNodeWithTag("filter priority 1").performClick()
        waitForIdle()

        assertEquals(TaskFilter(priorities = setOf(1)), state.taskFilter)
        onNodeWithTag(FILTER_COUNT_TAG).assertTextEquals("2 matches")
        onNodeWithText("Not done").performScrollTo().performClick()
        waitForIdle()
        assertEquals(TaskFilter(priorities = setOf(1), progress = setOf(ProgressFilter.NOT_DONE)), state.taskFilter)
        onNodeWithTag(FILTER_COUNT_TAG).assertTextEquals("1 match")
    }

    @Test
    fun overdueAndDueSoonAreOneOrTheOther() = runComposeUiTest {
        val state = shownApp().state
        press(Key.F, listOf(Key.CtrlLeft, Key.ShiftLeft))

        // The chips scroll when the bar is narrower than they are, so each is scrolled to first.
        onNodeWithText("Overdue").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Due in $FILTER_DUE_DAYS days").performScrollTo().performClick()
        waitForIdle()

        assertEquals(TaskFilter(dueWithinDays = FILTER_DUE_DAYS), state.taskFilter)
        onNodeWithTag(FILTER_COUNT_TAG).assertTextEquals("No matches")
    }

    @Test
    fun clearTakesTheFilterAwayAndEscClosesTheBar() = runComposeUiTest {
        val state = shownApp().state
        press(Key.F, listOf(Key.CtrlLeft, Key.ShiftLeft))
        onNodeWithTag("filter priority 1").performClick()
        waitForIdle()

        onNodeWithText("Clear").performClick()
        waitForIdle()
        assertNull(state.taskFilter)
        onNodeWithTag("filter priority 1").performClick()
        waitForIdle()

        press(Key.Escape)

        assertNull(state.taskFilter)
        assertTrue(onAllNodesWithTag(FILTER_COUNT_TAG).fetchSemanticsNodes().isEmpty(), "the bar is closed")
    }

    @Test
    fun viewMenuOpensTheBar() = runComposeUiTest {
        shownApp()

        onNodeWithText("View").performClick()
        waitForIdle()
        onNodeWithText("Filter...").performClick()
        waitForIdle()

        onNodeWithTag("filter priority 1").assertExists()
    }

    // The find bar's field keeps the test from ever being idle while it has the focus, so what it finds is checked
    // here, through what the field and F3 both find by.
    @Test
    fun findStepsOnlyThroughNodesTheFilterKeeps() = runComposeUiTest {
        val state = shownApp().state
        press(Key.F, listOf(Key.CtrlLeft, Key.ShiftLeft))
        onNodeWithTag("filter priority 1").performClick()
        waitForIdle()

        val matchIds = state.findMatchIds("Apple")
        val steps = findSteps(state, matchIds, from = state.idOf("Apple 1"))

        assertEquals(listOf("Apple 2", "Apple 4"), matchIds.map { state.textOf(it) })
        assertEquals(listOf("Apple 2", "Apple 4", "Apple 2"), steps.map { state.textOf(it) })
        state.taskFilter = null
        assertEquals(4, state.findMatchIds("Apple").size, "without the filter all are found")
    }

    private fun findSteps(
        state: MindMapState
        , matchIds: List<String>
        , from: String
    ): List<String> = generateSequence(nextMatch(state.tree, matchIds, from, FindDirection.NEXT)) {
        nextMatch(state.tree, matchIds, it, FindDirection.NEXT)
    }.take(3).toList()

    private fun MindMapState.textOf(id: String) = RichText.parse(tree.getNode(id).description).text
}
