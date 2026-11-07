package com.mindsync.library

import com.mindsync.library.data.NodeTask
import com.mindsync.library.data.TaskDate
import com.mindsync.library.data.TaskProgress
import com.mindsync.library.data.Tree
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeTaskStateTest {
    // The root with "a", "b" and "c" below it.
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            listOf("a", "b", "c").forEach { addNode(it, getRootNode().id, it.uppercase()) }
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    private fun MindMapState.selectNodes(vararg ids: String) {
        select(tree.getNode(ids.first()))
        ids.drop(1).forEach { toggleSelected(it) }
    }

    private fun MindMapState.progressOf(vararg ids: String) = ids.map { tree.taskOf(it).progress }

    @Test
    fun priorityIsGivenToTheSelectedNodesAsOneChange() {
        val state = state()
        state.tree.setTask("b", NodeTask(priority = 5, due = TaskDate(2026, 12, 1)))
        state.selectNodes("a", "b")

        state.setPriority(2)

        assertEquals(listOf(2, 2, null), listOf("a", "b", "c").map { state.tree.taskOf(it).priority })
        assertEquals(TaskDate(2026, 12, 1), state.tree.taskOf("b").due, "the rest of the task is kept")
        state.undo()
        assertEquals(listOf(null, 5, null), listOf("a", "b", "c").map { state.tree.taskOf(it).priority })
        assertFalse(state.canUndo)
    }

    @Test
    fun priorityIsTakenAway() {
        val state = state()
        state.selectNodes("a")
        state.setPriority(1)

        state.setPriority(null)

        assertTrue(state.tree.taskOf("a").isEmpty)
    }

    @Test
    fun priorityIsOneOfOneToNine() {
        val state = state()
        state.selectNodes("a")

        assertFailsWith<IllegalArgumentException> { state.setPriority(10) }
        assertFalse(state.canUndo)
    }

    @Test
    fun theRootCanBeATask() {
        val state = state()
        val rootId = state.tree.getRootNode().id
        state.select(state.tree.getRootNode())

        state.setProgress(TaskProgress.HALF)

        assertEquals(TaskProgress.HALF, state.tree.taskOf(rootId).progress)
    }

    @Test
    fun doneIsToggledForAllOfTheSelectedNodes() {
        val state = state()
        state.tree.setTask("a", NodeTask(progress = TaskProgress.DONE))
        state.selectNodes("a", "b")

        state.toggleDone()
        assertEquals(listOf(TaskProgress.DONE, TaskProgress.DONE), state.progressOf("a", "b"))

        state.toggleDone()
        assertEquals(listOf(TaskProgress.NOT_STARTED, TaskProgress.NOT_STARTED), state.progressOf("a", "b"))
    }

    @Test
    fun progressIsSteppedOnAndStartsOverAfterDone() {
        val state = state()

        val steps = (1..6).map {
            state.stepProgress("c")
            state.tree.taskOf("c").progress
        }

        assertEquals(
            listOf(
                TaskProgress.NOT_STARTED, TaskProgress.QUARTER, TaskProgress.HALF, TaskProgress.THREE_QUARTERS
                , TaskProgress.DONE, TaskProgress.NOT_STARTED
            )
            , steps
        )
        state.undo()
        assertEquals(TaskProgress.DONE, state.tree.taskOf("c").progress)
    }

    @Test
    fun stepOfANodeThatIsNotThereChangesNothing() {
        val state = state()

        state.stepProgress("missing")

        assertFalse(state.canUndo)
    }

    @Test
    fun withNothingSelectedNothingChanges() {
        val state = state()
        state.select(null)

        state.setPriority(3)
        state.setProgress(TaskProgress.DONE)
        state.toggleDone()

        assertFalse(state.canUndo)
        assertNull(state.tree.taskOf("a").priority)
    }

    @Test
    fun settingWhatTheNodesHaveAlreadyIsNoChange() {
        val state = state()
        state.selectNodes("a")
        state.setPriority(4)
        state.undo()
        state.redo()

        state.setPriority(4)
        state.undo()

        assertNull(state.tree.taskOf("a").priority)
    }
}
