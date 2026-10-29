package com.mindsync.mindmapview.desktop

internal actual fun logFailure(failure: Throwable) = jsLogError(failure.stackTraceToString())

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsLogError(text: String): Unit = js("console.error(text)")
