package com.mindsync.mindmapview.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class MapNameTest {
    @Test
    fun mapIsNamedForItsFile() {
        assertEquals("plans.ccmm", mapName("plans.ccmm", isModified = false))
    }

    @Test
    fun newMapIsUntitled() {
        assertEquals("Untitled", mapName(null, isModified = false))
    }

    @Test
    fun mapWithUnsavedChangesIsMarked() {
        assertEquals("*plans.ccmm", mapName("plans.ccmm", isModified = true))
        assertEquals("*Untitled", mapName(null, isModified = true))
    }
}
