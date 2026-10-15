package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FindTest {
    // The root with "Granite" and "Basalt" below it, "Pink granite" below "Granite" and "Obsidian" on the left.
    private fun stateWithRocks(): MindMapState {
        val tree = Tree<Any>(Dp(150f), Dp(300f))
        val rootId = tree.getRootNode().id
        tree.addNode("granite", rootId, "Granite")
        tree.addNode("pink", "granite", "Pink <b>granite</b>")
        tree.addNode("basalt", rootId, "Basalt", note = "Dark, like <i>gabbro</i> but finer<br>than granite")
        tree.addNode("obsidian", rootId, "Obsidian", side = LayoutSide.LEFT)
        tree.setRootChildSide("obsidian", LayoutSide.LEFT)
        return MindMapState(tree)
    }

    private fun MindMapState.foundIds(text: String): List<String> = findNodes(text).map { it.id }

    @Test
    fun nodesAreFoundByTheirTextInTheOrderOfTheTree() {
        val state = stateWithRocks()

        assertEquals(listOf("granite", "pink", "basalt"), state.foundIds("granite"))
    }

    @Test
    fun caseIsIgnored() {
        val state = stateWithRocks()

        assertEquals(listOf("obsidian"), state.foundIds("OBSIDIAN"))
    }

    @Test
    fun noteIsSearchedAsTheTextItShows() {
        val state = stateWithRocks()

        assertEquals(listOf("basalt"), state.foundIds("like gabbro"))
        assertEquals(emptyList(), state.foundIds("<i>"))
        assertEquals(emptyList(), state.foundIds("<br>"))
    }

    @Test
    fun descriptionIsSearchedAsTheTextItShows() {
        val state = stateWithRocks()

        assertEquals(listOf("pink"), state.foundIds("pink granite"))
        assertEquals(emptyList(), state.foundIds("<b>"))
    }

    @Test
    fun nodesHiddenInAFoldedNodeAreFound() {
        val state = stateWithRocks()
        state.tree.setFolded("granite", true)

        assertEquals(listOf("granite", "pink", "basalt"), state.foundIds("granite"))
    }

    @Test
    fun blankTextFindsNothing() {
        val state = stateWithRocks()

        assertEquals(emptyList(), state.foundIds(""))
        assertEquals(emptyList(), state.foundIds("   "))
    }

    @Test
    fun findingIsNoChangeToUndo() {
        val state = stateWithRocks()

        state.findNodes("granite")

        assertFalse(state.canUndo)
    }
}
