package com.mindsync.mindmapview.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InlineEditKeysTest {
    // What a key does with a node selected, or while its text is typed when [isEditing].
    private fun action(
        key: Key
        , codePoint: Int = 0
        , isShift: Boolean = false
        , isCtrl: Boolean = false
        , isAlt: Boolean = false
        , isMeta: Boolean = false
        , isEditing: Boolean = false
        , hasSelectedNode: Boolean = true
    ) = inlineKeyAction(key, codePoint, isShift, isCtrl, isAlt, isMeta, isEditing, hasSelectedNode)

    @Test
    fun tabAddsAChildAndEnterASibling() {
        assertEquals(InlineKeyAction.AddNode(NewNodePlace.CHILD), action(Key.Tab))
        assertEquals(InlineKeyAction.AddNode(NewNodePlace.SIBLING_BELOW), action(Key.Enter))
        assertEquals(InlineKeyAction.AddNode(NewNodePlace.SIBLING_BELOW), action(Key.NumPadEnter))
        assertEquals(InlineKeyAction.AddNode(NewNodePlace.SIBLING_ABOVE), action(Key.Enter, isShift = true))
    }

    @Test
    fun f2EditsTheTextAndAltEnterOpensTheDialog() {
        assertEquals(InlineKeyAction.EditText, action(Key.F2))
        assertEquals(InlineKeyAction.OpenEditDialog, action(Key.Enter, isAlt = true))
    }

    @Test
    fun aCharacterStartsTypingWithIt() {
        assertEquals(InlineKeyAction.TypeText("q"), action(Key.Q, codePoint = 'q'.code))
        assertEquals(InlineKeyAction.TypeText("Q"), action(Key.Q, codePoint = 'Q'.code, isShift = true))
        assertEquals(InlineKeyAction.TypeText("7"), action(Key.Seven, codePoint = '7'.code))
        assertEquals(InlineKeyAction.TypeText("é"), action(Key.Unknown, codePoint = 'é'.code))
        assertEquals(InlineKeyAction.TypeText("?"), action(Key.Slash, codePoint = '?'.code, isShift = true))
    }

    @Test
    fun keysThatTypeNothingStartNoTyping() {
        assertNull(action(Key.Spacebar, codePoint = ' '.code))
        assertNull(action(Key.Plus, codePoint = '+'.code))
        assertNull(action(Key.DirectionLeft, codePoint = 0xFFFF))
        assertNull(action(Key.DirectionLeft, codePoint = 0xF702))
        assertNull(action(Key.Delete, codePoint = 0x7F))
        assertNull(action(Key.Backspace, codePoint = 0x08))
        assertNull(action(Key.F5))
    }

    @Test
    fun shortcutsStartNoTyping() {
        assertNull(action(Key.C, codePoint = 'c'.code, isCtrl = true))
        assertNull(action(Key.F, codePoint = 'f'.code, isAlt = true))
        assertNull(action(Key.C, codePoint = 'c'.code, isMeta = true))
        assertNull(action(Key.Tab, isCtrl = true))
        assertNull(action(Key.Tab, isShift = true))
        assertNull(action(Key.Enter, isCtrl = true))
    }

    @Test
    fun withNoNodeSelectedNoKeyActs() {
        assertNull(action(Key.Tab, hasSelectedNode = false))
        assertNull(action(Key.Enter, hasSelectedNode = false))
        assertNull(action(Key.Q, codePoint = 'q'.code, hasSelectedNode = false))
    }

    @Test
    fun whileTypingEnterEndsTabAddsAChildAndEscapeCancels() {
        assertEquals(InlineKeyAction.Commit, action(Key.Enter, isEditing = true))
        assertEquals(InlineKeyAction.CommitAndAddChild, action(Key.Tab, isEditing = true))
        assertEquals(InlineKeyAction.Cancel, action(Key.Escape, isEditing = true))
    }

    @Test
    fun whileTypingOtherKeysGoToTheText() {
        assertNull(action(Key.Enter, isShift = true, isEditing = true))
        assertNull(action(Key.Q, codePoint = 'q'.code, isEditing = true))
        assertNull(action(Key.Spacebar, codePoint = ' '.code, isEditing = true))
        assertNull(action(Key.F2, isEditing = true))
        assertNull(action(Key.Z, isCtrl = true, isEditing = true))
        assertNull(action(Key.B, isCtrl = true, isEditing = true))
        assertNull(action(Key.DirectionLeft, isCtrl = true, isEditing = true))
    }

    @Test
    fun whileTypingMapShortcutsEndTheTypingAndGoOn() {
        listOf(Key.S, Key.O, Key.N, Key.F, Key.Comma).forEach {
            assertEquals(InlineKeyAction.CommitAndGoOn, action(it, isCtrl = true, isEditing = true), "$it")
        }
        assertEquals(InlineKeyAction.CommitAndGoOn, action(Key.F, isAlt = true, isEditing = true))
    }

    @Test
    fun charactersBeyondTheBasicPlaneAreTypedWhole() {
        assertEquals("😀", typedText(0x1F600))
        assertNull(typedText(0x110000))
    }

    @Test
    fun menusListTheKeysThatAddNodes() {
        assertEquals(
            listOf(
                "Tab" to NewNodePlace.CHILD
                , "Enter" to NewNodePlace.SIBLING_BELOW
                , "Shift+Enter" to NewNodePlace.SIBLING_ABOVE
            )
            , NEW_NODE_ITEMS.map { it.second to it.third }
        )
    }
}
