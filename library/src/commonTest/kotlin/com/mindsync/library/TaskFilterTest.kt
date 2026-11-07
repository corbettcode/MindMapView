package com.mindsync.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskDate
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.data.Tree
import com.mindsync.library.model.ProgressFilter
import com.mindsync.library.model.TaskFilter
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TaskFilterTest {
    private val today = TaskDate(2026, 11, 7)

    private fun TaskFilter.matches(task: NodeTask) = matches(task, today)

    // The root with "p" and "q" below it, "a" and "b" below "p", and "c" below "q". Only "a" is priority 1.
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(400f), Dp(300f)).apply {
            addNode("p", getRootNode().id, "P")
            addNode("q", getRootNode().id, "Q")
            addNode("a", "p", "A")
            addNode("b", "p", "B")
            addNode("c", "q", "C")
            setTask("a", NodeTask(priority = 1))
            setTask("b", NodeTask(priority = 2))
            setTask("c", NodeTask(priority = 3, progress = TaskProgress.DONE))
        }
        return MindMapState(tree).apply {
            setTree(tree)
            today = this@TaskFilterTest.today
        }
    }

    @Test
    fun emptyFilterMatchesEveryNode() {
        assertTrue(TaskFilter().isEmpty)
        assertTrue(TaskFilter().matches(NodeTask()))
        assertTrue(TaskFilter().matches(NodeTask(priority = 4, progress = TaskProgress.DONE)))
    }

    @Test
    fun priorityIsOneOfThoseGiven() {
        val filter = TaskFilter(priorities = setOf(1, 2))

        assertTrue(filter.matches(NodeTask(priority = 2)))
        assertFalse(filter.matches(NodeTask(priority = 3)))
        assertFalse(filter.matches(NodeTask()), "no priority is none of them")
    }

    @Test
    fun notDoneIsATaskThatIsNotDone() {
        val notDone = TaskFilter(progress = setOf(ProgressFilter.NOT_DONE))
        val done = TaskFilter(progress = setOf(ProgressFilter.DONE))
        val either = TaskFilter(progress = ProgressFilter.entries.toSet())

        assertTrue(notDone.matches(NodeTask(priority = 1)))
        assertTrue(notDone.matches(NodeTask(progress = TaskProgress.THREE_QUARTERS)))
        assertFalse(notDone.matches(NodeTask(progress = TaskProgress.DONE)))
        assertFalse(notDone.matches(NodeTask()), "a node that is no task")
        assertTrue(done.matches(NodeTask(progress = TaskProgress.DONE)))
        assertFalse(done.matches(NodeTask(progress = TaskProgress.HALF)))
        assertTrue(either.matches(NodeTask(progress = TaskProgress.DONE)))
        assertFalse(either.matches(NodeTask()))
    }

    @Test
    fun overdueIsDueBeforeTodayAndNotDone() {
        val filter = TaskFilter(isOverdueOnly = true)

        assertTrue(filter.matches(NodeTask(due = TaskDate(2026, 11, 6))))
        assertFalse(filter.matches(NodeTask(due = today)))
        assertFalse(filter.matches(NodeTask(progress = TaskProgress.DONE, due = TaskDate(2026, 11, 6))))
        assertFalse(filter.matches(NodeTask()))
        assertFalse(filter.matches(NodeTask(due = TaskDate(2026, 11, 6)), null), "without today nothing is overdue")
    }

    @Test
    fun dueWithinDaysIsFromTodayOn() {
        val filter = TaskFilter(dueWithinDays = 7)

        assertTrue(filter.matches(NodeTask(due = today)))
        assertTrue(filter.matches(NodeTask(due = TaskDate(2026, 11, 14))))
        assertFalse(filter.matches(NodeTask(due = TaskDate(2026, 11, 15))))
        assertFalse(filter.matches(NodeTask(due = TaskDate(2026, 11, 6))), "overdue already")
        assertFalse(filter.matches(NodeTask(progress = TaskProgress.DONE, due = today)))
        assertFalse(filter.matches(NodeTask(due = today), null))
        assertTrue(TaskFilter(dueWithinDays = Int.MAX_VALUE).matches(NodeTask(due = TaskDate(9999, 12, 31))))
    }

    @Test
    fun everyConditionSetIsMet() {
        val filter = TaskFilter(priorities = setOf(1, 2), progress = setOf(ProgressFilter.NOT_DONE))

        assertTrue(filter.matches(NodeTask(priority = 1, progress = TaskProgress.HALF)))
        assertFalse(filter.matches(NodeTask(priority = 1, progress = TaskProgress.DONE)))
        assertFalse(filter.matches(NodeTask(priority = 5, progress = TaskProgress.HALF)))
    }

    @Test
    fun daysAreCountedAcrossMonthsAndYears() {
        assertEquals(0L, TaskDate(1970, 1, 1).toEpochDay())
        assertEquals(TaskDate(2028, 3, 1), TaskDate(2028, 2, 28).plusDays(2), "2028 is a leap year")
        assertEquals(TaskDate(2026, 12, 31), TaskDate(2027, 1, 1).plusDays(-1))
        assertEquals(TaskDate(1, 1, 1), TaskDate.ofEpochDay(TaskDate(1, 1, 1).toEpochDay()))
        assertFailsWith<IllegalArgumentException> { TaskDate(9999, 12, 31).plusDays(1) }
    }

    @Test
    fun nodesThatDoNotMatchAreFadedButTheWayToAMatchIsNot() {
        val state = state()

        state.taskFilter = TaskFilter(priorities = setOf(1))

        assertEquals(setOf("b", "q", "c"), state.taskFilterView().fadedIds)
        assertEquals(listOf(true, false), listOf("a", "p").map { state.matchesTaskFilter(it) })
        assertFalse(state.canUndo, "a filter is no change to the map")
    }

    @Test
    fun withoutAFilterNothingIsFaded() {
        val state = state()
        state.taskFilter = TaskFilter(priorities = setOf(9))
        assertEquals(state.tree.nodes.keys, state.taskFilterView().fadedIds, "with no match all is faded")

        state.taskFilter = null

        assertTrue(state.taskFilterView().fadedIds.isEmpty())
        assertTrue(state.matchesTaskFilter("b"))
    }

    @Test
    fun foldedNodeWithAHiddenMatchIsMarked() {
        val state = state()
        state.tree.setFolded("p", true)
        state.tree.setFolded("q", true)

        state.taskFilter = TaskFilter(priorities = setOf(2))

        val view = state.taskFilterView()
        assertEquals(setOf("p"), view.hiddenMatchIds)
        assertTrue("q" in view.fadedIds)
    }

    @Test
    fun filterFadesTheScreenButNotAnExport() = runComposeUiTest {
        val state = state()
        setContent { MindMap(state = state, modifier = Modifier.size(800.dp, 600.dp)) }
        waitForIdle()
        state.animateTreeChange()
        waitForIdle()
        val screen = onRoot().captureToImage().toPixelMap().buffer.toList()
        val export = assertNotNull(state.exportImage()).toPixelMap().buffer.toList()

        state.taskFilter = TaskFilter(priorities = setOf(1))
        waitForIdle()

        assertNotEquals(screen, onRoot().captureToImage().toPixelMap().buffer.toList(), "the screen is faded")
        assertEquals(export, assertNotNull(state.exportImage()).toPixelMap().buffer.toList(), "the export is not")
    }
}
