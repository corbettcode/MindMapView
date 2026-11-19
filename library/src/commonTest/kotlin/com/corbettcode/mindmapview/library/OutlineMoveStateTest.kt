package com.corbettcode.mindmapview.library

import com.corbettcode.mindmapview.library.data.DpOffset
import com.corbettcode.mindmapview.library.data.Tree
import com.corbettcode.mindmapview.library.model.LayoutSide
import com.corbettcode.mindmapview.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutlineMoveStateTest {
    // The root with "p" on its right and "q" on its left; "b", "c", "d" and "e" below "p", and "f" below "b".
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            val rootId = getRootNode().id
            addNode("p", rootId, "P")
            addNode("q", rootId, "Q", side = LayoutSide.LEFT)
            listOf("b", "c", "d", "e").forEach { addNode(it, "p", it.uppercase()) }
            addNode("f", "b", "F")
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    private fun MindMapState.selectNodes(vararg ids: String) {
        select(tree.getNode(ids.first()))
        ids.drop(1).forEach { toggleSelected(it) }
    }

    private fun MindMapState.childrenOf(id: String) = tree.getNode(id).children

    @Test
    fun indentedTopicIsTheLastChildOfTheSiblingBeforeIt() {
        val state = state()
        state.selectNodes("c")

        state.indent()

        assertEquals(listOf("f", "c"), state.childrenOf("b"))
        assertEquals(listOf("b", "d", "e"), state.childrenOf("p"))
        assertEquals("b", state.selectedNode?.parentId)

        state.undo()
        assertEquals(listOf("b", "c", "d", "e"), state.childrenOf("p"))
        assertFalse(state.canUndo)
    }

    @Test
    fun siblingsAreIndentedTogetherInTheirOrder() {
        val state = state()
        state.selectNodes("e", "c", "d")

        state.indent()

        assertEquals(listOf("f", "c", "d", "e"), state.childrenOf("b"))
        assertEquals(listOf("c", "d", "e"), state.selectedNodes.map { it.id }.sorted())
        state.undo()
        assertEquals(listOf("b", "c", "d", "e"), state.childrenOf("p"))
    }

    @Test
    fun firstChildIsNotIndented() {
        val state = state()
        state.selectNodes("b")

        state.indent()

        assertEquals(listOf("b", "c", "d", "e"), state.childrenOf("p"))
        assertFalse(state.canUndo)
    }

    @Test
    fun outdentedTopicGoesRightAfterItsParent() {
        val state = state()
        state.selectNodes("f")

        state.outdent()

        assertEquals(listOf("b", "f", "c", "d", "e"), state.childrenOf("p"))
        assertTrue(state.childrenOf("b").isEmpty())
    }

    @Test
    fun topicOutdentedToTheRootKeepsItsSide() {
        val state = state()
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("g", "q", "G")
        state.selectNodes("g")

        state.outdent()

        assertEquals(listOf("p", "q", "g"), state.childrenOf(rootId))
        assertEquals(LayoutSide.LEFT, state.tree.sideOf("g"))
    }

    @Test
    fun childrenOfTheRootAndTheRootAreNotOutdented() {
        val state = state()
        state.selectNodes("p")
        state.toggleSelected(state.tree.getRootNode().id)

        state.outdent()

        assertFalse(state.canUndo)
    }

    @Test
    fun twoSiblingsAreOutdentedInTheirOrder() {
        val state = state()
        state.selectNodes("d", "c")

        state.outdent()

        assertEquals(listOf("p", "c", "d", "q"), state.childrenOf(state.tree.getRootNode().id))
        assertEquals(listOf("b", "e"), state.childrenOf("p"))
    }

    @Test
    fun topicsMoveUpAndDownAmongTheirSiblings() {
        val state = state()
        state.selectNodes("c", "d")

        state.moveAmongSiblings(isUp = true)
        assertEquals(listOf("c", "d", "b", "e"), state.childrenOf("p"))

        state.moveAmongSiblings(isUp = false)
        state.moveAmongSiblings(isUp = false)
        assertEquals(listOf("b", "e", "c", "d"), state.childrenOf("p"))

        state.undo()
        assertEquals(listOf("b", "c", "d", "e"), state.childrenOf("p"))
    }

    @Test
    fun topicsAtTheEndDoNotMove() {
        val state = state()
        state.selectNodes("b", "d")

        state.moveAmongSiblings(isUp = true)
        assertEquals(listOf("b", "d", "c", "e"), state.childrenOf("p"))

        state.moveAmongSiblings(isUp = true)
        assertEquals(listOf("b", "d", "c", "e"), state.childrenOf("p"))
    }

    @Test
    fun childOfTheRootKeepsItsSideAsItMoves() {
        val state = state()
        state.selectNodes("q")

        state.moveAmongSiblings(isUp = true)

        assertEquals(listOf("q", "p"), state.childrenOf(state.tree.getRootNode().id))
        assertEquals(LayoutSide.LEFT, state.tree.sideOf("q"))
    }

    @Test
    fun indentedSummaryIsAnOrdinaryTopic() {
        val state = state()
        state.tree.addNode("s", "p", "S", index = 2)
        state.tree.setSummary("s", setOf("b", "c"))
        state.selectNodes("s")

        state.indent()

        assertFalse(state.tree.isSummary("s"))
        assertEquals("c", state.tree.getNode("s").parentId)
    }

    @Test
    fun indentedFloatingTopicIsAttachedToTheTopicAbove() {
        val state = state()
        state.tree.setFloating("q", DpOffset(Dp(0f), Dp(300f)))
        state.selectNodes("q")

        state.indent()

        assertEquals("p", state.tree.getNode("q").parentId)
        assertFalse(state.tree.isFloating("q"))
    }

    @Test
    fun topicOutdentedFromAFloatingOneIsAttachedToTheRoot() {
        val state = state()
        state.tree.setFloating("p", DpOffset(Dp(0f), Dp(300f)))
        state.selectNodes("c")

        state.outdent()

        assertEquals(listOf("q", "c", "p"), state.childrenOf(state.tree.getRootNode().id))
        assertFalse(state.tree.isFloating("c"))
        assertEquals(LayoutSide.RIGHT, state.tree.sideOf("c"))
    }

    @Test
    fun floatingTopicsMoveOnlyAmongThemselves() {
        val state = state()
        val rootId = state.tree.getRootNode().id
        state.tree.addNode("r", rootId, "R")
        state.tree.setFloating("q", DpOffset(Dp(0f), Dp(300f)))
        state.tree.setFloating("r", DpOffset(Dp(0f), Dp(400f)))
        state.selectNodes("r")

        state.moveAmongSiblings(isUp = true)
        assertEquals(listOf("p", "r", "q"), state.childrenOf(rootId))

        state.moveAmongSiblings(isUp = true)
        assertEquals(listOf("p", "r", "q"), state.childrenOf(rootId), "not above the attached topics")
        assertTrue(state.tree.isFloating("r"))
    }
}

