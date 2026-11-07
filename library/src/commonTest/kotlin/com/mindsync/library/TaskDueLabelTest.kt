package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.RectangleNodeData
import com.mindsync.library.data.TaskDate
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskDueLabelTest {
    // The root in the middle with "a" below it, shown on the screen, with its due dates written by [label].
    private fun ComposeUiTest.shownState(label: (TaskDate) -> String = { it.toIsoString() }): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply { addNode("a", getRootNode().id, "Apples") }
        val state = MindMapState(tree).apply { setTree(tree) }
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp), dateLabel = label) }
        waitForIdle()
        state.animateTreeChange()
        waitForIdle()
        return state
    }

    private fun MindMapState.sizeOf(id: String): Pair<Float, Float> =
        (tree.getNode(id) as RectangleNodeData).path.let { it.width.dpVal to it.height.dpVal }

    private fun MindMapState.contentOf(id: String) = mindMapManager.getMeasureTextSize()!!
        .measureContent(tree, tree.getNode(id), attributeDisplay, noteDisplay)

    // Pixels much more red than green or blue, which no gray text and no anti-aliasing of it has.
    private fun PixelMap.redCount(): Int = (0 until height).sumOf { y ->
        (0 until width).count { x -> this[x, y].let { it.red > 0.6f && it.green < 0.35f && it.blue < 0.35f } }
    }

    private fun ComposeUiTest.redCountOf(state: MindMapState): Int {
        waitForIdle()
        return assertNotNull(state.exportImage()).toPixelMap().redCount()
    }

    @Test
    fun dueDateIsALineBelowTheText() = runComposeUiTest {
        val state = shownState()
        val (_, height) = state.sizeOf("a")

        state.select(state.tree.getNode("a"))
        state.setDates(null, TaskDate(2026, 11, 30))
        waitForIdle()

        val content = state.contentOf("a")
        val label = assertNotNull(content.dueLabel)
        assertEquals("2026-11-30", label.layoutInput.text.text)
        assertTrue(state.sizeOf("a").second > height + with(density) { label.size.height.toDp().value })
        assertTrue(content.dueTop > -content.textRise, "the date is below the text")
    }

    @Test
    fun dueDateIsWrittenByTheLabelGiven() = runComposeUiTest {
        val state = shownState { "Due on the ${it.day}th of a long month" }
        val (width, _) = state.sizeOf("a")

        state.select(state.tree.getNode("a"))
        state.setDates(TaskDate(2026, 11, 1), TaskDate(2026, 11, 30))
        waitForIdle()

        assertEquals("Due on the 30th of a long month", state.contentOf("a").dueLabel?.layoutInput?.text?.text)
        assertTrue(state.sizeOf("a").first > width, "the node is as wide as its longer label")
    }

    @Test
    fun startDateAloneIsNotDrawn() = runComposeUiTest {
        val state = shownState()
        val size = state.sizeOf("a")

        state.select(state.tree.getNode("a"))
        state.setDates(TaskDate(2026, 11, 1), null)
        waitForIdle()

        assertEquals(null, state.contentOf("a").dueLabel)
        assertEquals(size, state.sizeOf("a"))
    }

    @Test
    fun overdueDateIsRedUntilDone() = runComposeUiTest {
        val state = shownState()
        state.tree.setTask("a", NodeTask(due = TaskDate(2026, 11, 6)))
        state.animateTreeChange()
        val notOverdue = redCountOf(state)

        state.today = TaskDate(2026, 11, 7)
        val overdue = redCountOf(state)
        state.select(state.tree.getNode("a"))
        state.setProgress(TaskProgress.DONE)
        val done = redCountOf(state)

        assertTrue(overdue > notOverdue, "overdue is red: $overdue red pixels, $notOverdue before")
        assertEquals(notOverdue, done, "done is not overdue")
    }
}
