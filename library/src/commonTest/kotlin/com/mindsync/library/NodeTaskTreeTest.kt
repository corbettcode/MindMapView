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

class NodeTaskTreeTest {
    // The root with "a" below it, and "b" below "a".
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("a", getRootNode().id, "A")
        addNode("b", "a", "B")
    }

    private val task = NodeTask(
        priority = 2
        , progress = TaskProgress.HALF
        , start = TaskDate(2026, 11, 1)
        , due = TaskDate(2026, 11, 30)
    )

    @Test
    fun dateIsWrittenAsInIso8601() {
        assertEquals("2026-11-06", TaskDate(2026, 11, 6).toIsoString())
        assertEquals("0005-01-02", TaskDate(5, 1, 2).toIsoString())
    }

    @Test
    fun dateIsReadAsItIsWritten() {
        assertEquals(TaskDate(2026, 11, 6), TaskDate.parse("2026-11-06"))
        assertEquals(TaskDate(2026, 11, 6), TaskDate.parse(" 2026-11-06 "))
        assertEquals(TaskDate(2024, 2, 29), TaskDate.parse("2024-02-29"))
        assertEquals(TaskDate(2000, 2, 29), TaskDate.parse("2000-02-29"))
    }

    @Test
    fun noSuchDayIsReadAsNone() {
        listOf(
            "2026-02-29", "2100-02-29", "2026-02-30", "2026-04-31", "2026-13-01", "2026-00-10", "2026-01-00"
            , "0000-01-01", "26-1-1", "2026/11/06", "2026-11-06T10:00", "", "soon"
        ).forEach { assertNull(TaskDate.parse(it), it) }
    }

    @Test
    fun noSuchDayIsNoDate() {
        assertFailsWith<IllegalArgumentException> { TaskDate(2026, 2, 29) }
        assertFailsWith<IllegalArgumentException> { TaskDate(2026, 6, 31) }
    }

    @Test
    fun datesAreInTheOrderOfTheCalendar() {
        val dates = listOf(TaskDate(2027, 1, 1), TaskDate(2026, 12, 31), TaskDate(2026, 2, 1), TaskDate(2026, 1, 31))

        assertEquals(dates.reversed(), dates.sorted())
    }

    @Test
    fun progressStepsOnAndStartsOverAfterDone() {
        val steps = generateSequence(TaskProgress.NOT_STARTED) { it.next }.take(6).toList()

        assertEquals(
            listOf(
                TaskProgress.NOT_STARTED, TaskProgress.QUARTER, TaskProgress.HALF, TaskProgress.THREE_QUARTERS
                , TaskProgress.DONE, TaskProgress.NOT_STARTED
            )
            , steps
        )
        assertEquals(listOf(0, 25, 50, 75, 100), TaskProgress.entries.map { it.percent })
    }

    @Test
    fun priorityIsOneOfOneToNine() {
        assertFailsWith<IllegalArgumentException> { NodeTask(priority = 0) }
        assertFailsWith<IllegalArgumentException> { NodeTask(priority = 10) }
        assertEquals(9, NodeTask(priority = 9).priority)
    }

    @Test
    fun taskWithNothingIsEmpty() {
        assertTrue(NodeTask().isEmpty)
        assertFalse(NodeTask(progress = TaskProgress.NOT_STARTED).isEmpty)
        assertFalse(NodeTask(due = TaskDate(2026, 1, 1)).isEmpty)
    }

    @Test
    fun nodeHoldsItsTask() {
        val tree = tree()

        tree.setTask("b", task)

        assertEquals(task, tree.taskOf("b"))
        assertTrue(tree.taskOf("a").isEmpty)
        assertTrue(tree.taskOf(tree.getRootNode().id).isEmpty)
    }

    @Test
    fun emptyTaskMakesTheNodeNoTask() {
        val tree = tree()
        tree.setTask("b", task)

        tree.setTask("b", NodeTask())

        assertTrue(tree.taskOf("b").isEmpty)
        assertTrue(tree.snapshot().hasSameContentAs(tree().snapshot()))
    }

    @Test
    fun nodeThatIsNotInTheTreeGetsNoTask() {
        assertFailsWith<IllegalArgumentException> { tree().setTask("missing", task) }
    }

    @Test
    fun tasksArePartOfTheMapAndComeBackWithIt() {
        val tree = tree()
        val before = tree.snapshot()

        tree.setTask("a", task)
        val withTask = tree.snapshot()
        tree.restore(before)

        assertFalse(before.hasSameContentAs(withTask))
        assertTrue(tree.taskOf("a").isEmpty)
        tree.restore(withTask)
        assertEquals(task, tree.taskOf("a"))
    }

    @Test
    fun copiedNodesArePastedWithTheirTasks() {
        val tree = tree()
        tree.setTask("a", task)
        tree.setTask("b", NodeTask(progress = TaskProgress.DONE))
        val state = MindMapState(tree).apply { setTree(tree) }
        state.select(tree.getNode("a"))
        state.copyNode()

        state.select(tree.getRootNode())
        state.pasteNode()

        val pastedA = tree.getRootNode().children.last()
        val pastedB = tree.getNode(pastedA).children.single()
        assertEquals(task, tree.taskOf(pastedA))
        assertEquals(TaskProgress.DONE, tree.taskOf(pastedB).progress)
    }
}
