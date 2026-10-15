package com.mindsync.library

/**
 * What there is to undo and redo, each step as the [S] it goes back to. The oldest steps are forgotten
 * beyond [limit] of them.
 */
internal class UndoHistory<S>(
    private val limit: Int = DEFAULT_LIMIT
) {
    private val undoSteps = ArrayDeque<S>()
    private val redoSteps = ArrayDeque<S>()

    val canUndo: Boolean get() = undoSteps.isNotEmpty()

    val canRedo: Boolean get() = redoSteps.isNotEmpty()

    /** Records [before], as it was before a change, to undo the change to. */
    fun record(before: S) {
        undoSteps.addLast(before)
        if (undoSteps.size > limit) undoSteps.removeFirst()
        redoSteps.clear()
    }

    /** Returns what to go back to, keeping [current] to redo, or null when there is nothing to undo. */
    fun undo(current: S): S? = undoSteps.removeLastOrNull()?.also { redoSteps.addLast(current) }

    /** Returns what was undone last, keeping [current] to undo, or null when there is nothing to redo. */
    fun redo(current: S): S? = redoSteps.removeLastOrNull()?.also { undoSteps.addLast(current) }

    fun clear() {
        undoSteps.clear()
        redoSteps.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
