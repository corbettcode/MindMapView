package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.TaskDate

internal actual fun today(): TaskDate = TaskDate(jsLocalYear(), jsLocalMonth() + 1, jsLocalDay())

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsLocalYear(): Int = js("new Date().getFullYear()")

// JavaScript counts months from 0.
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsLocalMonth(): Int = js("new Date().getMonth()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsLocalDay(): Int = js("new Date().getDate()")
