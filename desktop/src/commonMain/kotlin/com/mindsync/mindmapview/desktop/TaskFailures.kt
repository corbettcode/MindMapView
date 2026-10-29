package com.mindsync.mindmapview.desktop

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * A scope of [parent] in which a task that fails is passed to [onFailure] rather than escaping. An escaped failure
 * would cancel the scope, so no task launched after it would run, and a browser would only log that a failure could
 * not be handled, without saying which.
 */
internal fun failureSafeScope(
    parent: CoroutineScope
    , onFailure: (Throwable) -> Unit
): CoroutineScope = CoroutineScope(
    parent.coroutineContext
        + SupervisorJob(parent.coroutineContext[Job])
        + CoroutineExceptionHandler { _, failure -> onFailure(failure) }
)

/** What the user is told about a [failure] nothing else handled. */
internal fun failureMessage(
    failure: Throwable
): String = "Something went wrong: ${failure.message ?: failure::class.simpleName}"

/** Logs [failure] with its causes and where it happened, for it to be found. */
internal expect fun logFailure(failure: Throwable)
