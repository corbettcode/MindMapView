package com.mindsync.mindmapview.desktop

import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskFailuresTest {
    // A failure that escaped would cancel the scope, so no task launched after it would run.
    @Test
    fun failedTaskIsReportedAndLaterTasksStillRun() = runBlocking<Unit> {
        val failures = mutableListOf<Throwable>()
        val scope = failureSafeScope(this) { failures += it }

        scope.launch { throw IllegalStateException("broken") }.join()
        var hasRun = false
        scope.launch { hasRun = true }.join()

        assertEquals(listOf("broken"), failures.map { it.message })
        assertTrue(hasRun)
        assertTrue(isActive)
        scope.cancel()
    }

    @Test
    fun messageTellsWhatFailed() =
        assertEquals("Something went wrong: broken", failureMessage(IllegalStateException("broken")))

    @Test
    fun failureWithoutAMessageIsNamedByItsKind() =
        assertEquals("Something went wrong: IllegalStateException", failureMessage(IllegalStateException()))
}
