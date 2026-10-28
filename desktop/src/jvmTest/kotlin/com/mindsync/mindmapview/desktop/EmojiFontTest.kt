package com.mindsync.mindmapview.desktop

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EmojiFontTest {
    // JUnit only runs test methods that return nothing, hence runBlocking<Unit>.
    @Test
    fun bundledFontLoads() = runBlocking<Unit> { assertNotNull(loadEmojiFontFamily()) }

    @Test
    fun missingFontGivesNull() = runBlocking<Unit> { assertNull(loadEmojiFontFamily("missing.ttf")) }
}
