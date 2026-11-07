package com.mindsync.library

import com.mindsync.library.data.Tree
import com.mindsync.library.io.FreeMind
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SummaryStateTest {
    // The root with "p" and "r" on its right and "q" on its left; "b", "c" and "d" below "p".
    private fun state(): MindMapState {
        val tree = Tree<Any>(Dp(0f), Dp(0f)).apply {
            val rootId = getRootNode().id
            addNode("p", rootId, "P")
            addNode("q", rootId, "Q", side = LayoutSide.LEFT)
            addNode("r", rootId, "R")
            listOf("b", "c", "d").forEach { addNode(it, "p", it.uppercase()) }
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    private fun MindMapState.selectNodes(vararg ids: String) {
        select(tree.getNode(ids.first()))
        ids.drop(1).forEach { toggleSelected(it) }
    }

    private fun MindMapState.childrenOf(id: String) = tree.getNode(id).children

    // What there is of the map but where its nodes are drawn.
    private fun MindMapState.content(): String = FreeMind.write(tree)

    @Test
    fun summaryIsAddedRightAfterItsSiblings() {
        val state = state()
        state.selectNodes("b", "c")

        state.addSummaryNode("Sum")

        val summary = assertNotNull(state.getAddNode())
        assertEquals(listOf("b", "c", summary.id, "d"), state.childrenOf("p"))
        assertEquals(listOf("b", "c"), state.tree.summaryChildIds(summary.id))
        assertEquals("Sum", state.tree.getNode(summary.id).description)
        assertEquals("c", state.selectedNode?.id)
    }

    @Test
    fun eachParentAndSideGetsASummaryOfItsOwn() {
        val state = state()
        val rootId = state.tree.getRootNode().id
        state.selectNodes("b", "p", "q", "r")

        state.addSummaryNode("Sum")

        // "b" is below "p", so "p" stands for its branch.
        val summaries = state.tree.summaries.values.map { it.toSet() }.toSet()
        assertEquals(setOf(setOf("p", "r"), setOf("q")), summaries)
        val rootChildren = state.childrenOf(rootId)
        val leftSummary = state.tree.summaries.entries.single { it.value == listOf("q") }.key
        assertEquals(LayoutSide.LEFT, state.tree.sideOf(leftSummary))
        assertEquals(rootChildren.indexOf("r") + 1, rootChildren.indexOf(state.tree.summaries.entries.single {
            it.value == listOf("p", "r")
        }.key))
    }

    @Test
    fun theRootAndSummariesGetNoSummary() {
        val state = state()
        state.selectNodes("c")
        state.addSummaryNode("Sum")
        val summaryId = assertNotNull(state.getAddNode()).id
        val content = state.content()

        state.select(state.tree.getRootNode())
        state.addSummaryNode("Root sum")
        state.selectNodes(summaryId)
        state.addSummaryNode("Sum of sum")

        assertEquals(content, state.content())
    }

    @Test
    fun addingASummaryIsOneChange() {
        val state = state()
        state.selectNodes("b", "d")

        state.addSummaryNode("Sum")
        state.undo()

        assertEquals(listOf("b", "c", "d"), state.childrenOf("p"))
        assertTrue(state.tree.summaries.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun summaryIsTypedAsItIsAdded() {
        val state = state()
        val content = state.content()
        state.selectNodes("b", "c")

        state.addNodeForTextEdit { addSummaryNode("Summary") }
        val id = assertNotNull(state.textEditNodeId)
        assertTrue(state.tree.isSummary(id))
        state.cancelTextEdit()

        assertEquals(content, state.content())
        assertFalse(state.canUndo)

        state.selectNodes("b", "c")
        state.addNodeForTextEdit { addSummaryNode("Summary") }
        state.previewText("Fruit")
        state.commitTextEdit()
        val summaryId = state.tree.summaries.keys.single()
        assertEquals("Fruit", state.tree.getNode(summaryId).description)
        state.undo()
        assertTrue(state.tree.summaries.isEmpty())
    }

    @Test
    fun copiedBranchIsPastedWithTheSummariesWithinIt() {
        val state = state()
        state.selectNodes("b", "c")
        state.addSummaryNode("Sum")
        state.selectNodes("p")
        state.copyNode()

        state.selectNodes("q")
        state.pasteNode()

        val pastedP = state.childrenOf("q").single()
        val pastedChildren = state.childrenOf(pastedP)
        val pastedSummary = pastedChildren[2]
        assertTrue(state.tree.isSummary(pastedSummary))
        assertEquals(pastedChildren.take(2), state.tree.summaryChildIds(pastedSummary))
    }

    @Test
    fun copiedSummaryAloneIsPastedAsAnOrdinaryNode() {
        val state = state()
        state.selectNodes("c")
        state.addSummaryNode("Sum")
        state.selectNodes(assertNotNull(state.getAddNode()).id)
        state.copyNode()

        state.selectNodes("q")
        state.pasteNode()

        val pasted = state.childrenOf("q").single()
        assertFalse(state.tree.isSummary(pasted))
        assertEquals(1, state.tree.summaries.size)
    }
}
