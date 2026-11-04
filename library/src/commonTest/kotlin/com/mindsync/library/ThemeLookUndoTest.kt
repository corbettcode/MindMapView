package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.MapThemes
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeLookUndoTest {
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply { addNode("main", getRootNode().id, "Main") }
        return MindMapState(tree).apply { setTree(tree) }
    }

    @Test
    fun undoOfAThemeGivesBackWhatTheAppKeptWithTheThemeBefore() {
        val state = state().apply { themeLook = "classic look" }

        state.setTheme(MapThemes.byId("ocean"))
        state.themeLook = "ocean look"
        state.undo()

        assertEquals(MapThemes.CLASSIC, state.tree.theme)
        assertEquals("classic look", state.themeLook)

        state.redo()

        assertEquals("ocean", state.tree.theme.id)
        assertEquals("ocean look", state.themeLook)
    }

    @Test
    fun undoThatKeepsTheThemeKeepsWhatTheAppKeptSince() {
        val state = state().apply { themeLook = "before" }

        state.groupChanges { state.tree.updateNode("main", "Changed") }
        state.themeLook = "changed since"
        state.undo()

        assertEquals("changed since", state.themeLook)
    }
}
