package com.mindsync.mindmapview.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnsavedChangesTest {
    @Test
    fun titleNamesTheFileAndMarksUnsavedChanges() {
        assertEquals("rocks.mm - MindMapView", windowTitle("rocks.mm", isModified = false))
        assertEquals("*rocks.mm - MindMapView", windowTitle("rocks.mm", isModified = true))
    }

    @Test
    fun titleOfAMapWithoutAFileIsTheNameOfTheApp() {
        assertEquals("MindMapView", windowTitle(null, isModified = false))
        assertEquals("*MindMapView", windowTitle(null, isModified = true))
    }

    @Test
    fun questionNamesTheFileToSaveTheChangesTo() {
        assertEquals("Save changes to rocks.mm?", saveChangesQuestion("rocks.mm"))
        assertEquals("Save changes to this mind map?", saveChangesQuestion(null))
    }

    @Test
    fun savingGoesOnOnlyWhenTheMapIsSaved() {
        assertTrue(goesOn(SaveChoice.SAVE) { true })
        assertFalse(goesOn(SaveChoice.SAVE) { false })
    }

    @Test
    fun notSavingGoesOnWithoutSaving() {
        var isSaved = false

        assertTrue(goesOn(SaveChoice.DISCARD) { true.also { isSaved = true } })
        assertFalse(isSaved)
    }

    @Test
    fun cancellingGoesNoFurtherWithoutSaving() {
        var isSaved = false

        assertFalse(goesOn(SaveChoice.CANCEL) { true.also { isSaved = true } })
        assertFalse(isSaved)
    }
}
