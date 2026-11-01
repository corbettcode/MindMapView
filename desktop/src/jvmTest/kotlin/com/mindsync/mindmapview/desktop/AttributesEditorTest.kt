package com.mindsync.mindmapview.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mindsync.library.data.NodeAttribute
import com.mindsync.mindmapview.desktop.component.AttributesEditor
import com.mindsync.mindmapview.desktop.component.attributeRowsOf
import com.mindsync.mindmapview.desktop.component.attributesToKeep
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttributesEditorTest {
    @Test
    fun rowsWithoutANameAreNoAttributes() {
        val rows = listOf(NodeAttribute(" Owner ", "Ann"), NodeAttribute("", "lost"), NodeAttribute("  ", "x"))

        assertEquals(listOf(NodeAttribute("Owner", "Ann")), attributesToKeep(rows))
    }

    @Test
    fun aNodeWithoutAttributesStartsWithAnEmptyRow() {
        assertEquals(listOf(NodeAttribute("", "")), attributeRowsOf(emptyList()))
        assertEquals(listOf(NodeAttribute("a", "1")), attributeRowsOf(listOf(NodeAttribute("a", "1"))))
    }

    @Test
    fun attributesAreShownWithTheNamesOtherNodesHave() = runComposeUiTest {
        val rows = mutableStateListOf(NodeAttribute("Owner", "Ann"))
        setContent { AttributesEditor(rows = rows, knownNames = listOf("Owner", "Due"), isFocusedOnStart = false) }

        onNodeWithText("Ann").assertIsDisplayed()
        onAllNodesWithText("+ Owner").assertCountEquals(0)
        onNodeWithText("+ Due").performClick()
        waitForIdle()
        assertEquals(listOf(NodeAttribute("Owner", "Ann"), NodeAttribute("Due", "")), rows.toList())
    }

    @Test
    fun anEmptyEditorStartsInTheFirstName() = runComposeUiTest {
        val rows = mutableStateListOf(NodeAttribute("", ""))
        setContent { AttributesEditor(rows = rows, knownNames = emptyList(), isFocusedOnStart = true) }
        waitForIdle()

        onNodeWithText("Name").assertIsFocused()
    }
}
