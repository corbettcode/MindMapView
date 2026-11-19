package com.mindsync.mindmapview.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutlineKeysTest {
    private fun action(
        key: Key
        , isShift: Boolean = false
        , isCtrl: Boolean = false
        , isAlt: Boolean = false
        , codePoint: Int = 0
        , hasSelectedNode: Boolean = true
    ) = outlineKeyAction(key, codePoint, isShift, isCtrl, isAlt, isMeta = false, hasSelectedNode = hasSelectedNode)

    @Test
    fun tabIndentsAndShiftTabOutdents() {
        assertEquals(OutlineKeyAction.Move(OutlineMove.INDENT), action(Key.Tab))
        assertEquals(OutlineKeyAction.Move(OutlineMove.OUTDENT), action(Key.Tab, isShift = true))
    }

    @Test
    fun arrowKeysGoAlongTheRows() {
        assertEquals(OutlineKeyAction.SelectRow(isUp = true), action(Key.DirectionUp))
        assertEquals(OutlineKeyAction.SelectRow(isUp = false), action(Key.DirectionDown))
        assertEquals(OutlineKeyAction.FoldOrParent, action(Key.DirectionLeft))
        assertEquals(OutlineKeyAction.UnfoldOrChild, action(Key.DirectionRight))
    }

    @Test
    fun keysWithCtrlAreTheApps() {
        assertNull(action(Key.DirectionUp, isCtrl = true))
        assertNull(action(Key.S, isCtrl = true))
        assertNull(action(Key.DirectionUp, isAlt = true, isShift = true))
    }

    @Test
    fun enterF2AndTypingAreAsOnTheMap() {
        assertEquals(
            OutlineKeyAction.Inline(InlineKeyAction.AddNode(NewNodePlace.SIBLING_BELOW))
            , action(Key.Enter)
        )
        assertEquals(
            OutlineKeyAction.Inline(InlineKeyAction.AddNode(NewNodePlace.SIBLING_ABOVE))
            , action(Key.Enter, isShift = true)
        )
        assertEquals(OutlineKeyAction.Inline(InlineKeyAction.EditText), action(Key.F2))
        assertEquals(OutlineKeyAction.Inline(InlineKeyAction.OpenEditDialog), action(Key.Enter, isAlt = true))
        assertEquals(OutlineKeyAction.Inline(InlineKeyAction.TypeText("x")), action(Key.X, codePoint = 'x'.code))
    }

    @Test
    fun withNoTopicSelectedDownSelectsTheRootAndEnterAddsToIt() {
        assertEquals(OutlineKeyAction.SelectRow(isUp = false), action(Key.DirectionDown, hasSelectedNode = false))
        assertEquals(
            OutlineKeyAction.Inline(InlineKeyAction.AddNode(NewNodePlace.CHILD))
            , action(Key.Enter, hasSelectedNode = false)
        )
    }
}
