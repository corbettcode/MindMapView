package com.mindsync.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoHistoryTest {
    @Test
    fun emptyHistoryHasNothingToUndoOrRedo() {
        val history = UndoHistory<String>()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo("now"))
        assertNull(history.redo("now"))
    }

    @Test
    fun undoReturnsWhatWasRecordedLastAndRedoReturnsWhatWasUndone() {
        val history = UndoHistory<String>()
        history.record("first")
        history.record("second")

        assertEquals("second", history.undo("third"))
        assertEquals("first", history.undo("second"))
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
        assertEquals("second", history.redo("first"))
        assertEquals("third", history.redo("second"))
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun recordingAfterAnUndoLeavesNothingToRedo() {
        val history = UndoHistory<String>()
        history.record("first")
        history.undo("second")

        history.record("first")

        assertFalse(history.canRedo)
        assertNull(history.redo("other"))
    }

    @Test
    fun onlyTheLastStepsUpToTheLimitAreKept() {
        val history = UndoHistory<Int>(limit = 3)
        (1..5).forEach { history.record(it) }

        assertEquals(5, history.undo(6))
        assertEquals(4, history.undo(5))
        assertEquals(3, history.undo(4))
        assertFalse(history.canUndo)
    }

    @Test
    fun clearForgetsWhatThereIsToUndoAndRedo() {
        val history = UndoHistory<String>()
        history.record("first")
        history.record("second")
        history.undo("third")

        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
