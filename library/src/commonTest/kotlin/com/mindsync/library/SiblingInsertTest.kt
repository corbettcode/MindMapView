package com.mindsync.library

import androidx.compose.ui.graphics.Color
import com.mindsync.library.data.NodeShape
import com.mindsync.library.data.OvalNodeData
import com.mindsync.library.data.Tree
import com.mindsync.library.model.LayoutSide
import com.mindsync.library.util.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SiblingInsertTest {
    private fun tree(): Tree<Any> = Tree<Any>(Dp(0f), Dp(0f)).apply {
        addNode("a", getRootNode().id, "A")
        addNode("b", "a", "B")
        addNode("c", "a", "C")
    }

    private fun Tree<*>.childrenOf(id: String) = getNode(id).children

    @Test
    fun nodeWithoutAnIndexGoesAfterTheLastChild() {
        val tree = tree()

        tree.addNode("d", "a", "D")

        assertEquals(listOf("b", "c", "d"), tree.childrenOf("a"))
    }

    @Test
    fun nodeGoesAtItsIndexAmongTheChildren() {
        val first = tree().apply { addNode("d", "a", "D", index = 0) }
        val middle = tree().apply { addNode("d", "a", "D", index = 1) }
        val last = tree().apply { addNode("d", "a", "D", index = 2) }

        assertEquals(listOf("d", "b", "c"), first.childrenOf("a"))
        assertEquals(listOf("b", "d", "c"), middle.childrenOf("a"))
        assertEquals(listOf("b", "c", "d"), last.childrenOf("a"))
    }

    @Test
    fun indexPastEitherEndPutsTheNodeAtThatEnd() {
        val before = tree().apply { addNode("d", "a", "D", index = -3) }
        val after = tree().apply { addNode("d", "a", "D", index = 9) }

        assertEquals(listOf("d", "b", "c"), before.childrenOf("a"))
        assertEquals(listOf("b", "c", "d"), after.childrenOf("a"))
    }

    @Test
    fun childOfTheRootAtAnIndexKeepsItsSide() {
        val tree = tree()
        val rootId = tree.getRootNode().id
        tree.addNode("e", rootId, "E", side = LayoutSide.UPPER_LEFT)

        tree.addNode("f", rootId, "F", side = LayoutSide.UPPER_LEFT, index = 1)

        assertEquals(listOf("a", "f", "e"), tree.childrenOf(rootId))
        assertEquals(LayoutSide.UPPER_LEFT, tree.sideOf("f"))
        assertEquals(LayoutSide.UPPER_LEFT, tree.sideOf("e"))
        assertEquals(LayoutSide.RIGHT, tree.sideOf("a"))
    }

    // The root with "a" on the right, "b" and "c" below it, and "e" and "g" on the upper left.
    private fun state(): MindMapState {
        val tree = tree().apply {
            addNode("e", getRootNode().id, "E", side = LayoutSide.UPPER_LEFT)
            addNode("g", getRootNode().id, "G", side = LayoutSide.UPPER_LEFT)
        }
        return MindMapState(tree).apply { setTree(tree) }
    }

    private fun MindMapState.selectNode(id: String) = select(tree.getNode(id))

    private fun MindMapState.addedId(): String = getAddNode()!!.id

    @Test
    fun siblingGoesRightAfterTheSelectedNode() {
        val state = state()
        state.selectNode("b")

        state.addSiblingNode("D")

        assertEquals(listOf("b", state.addedId(), "c"), state.tree.childrenOf("a"))
        assertEquals("D", state.tree.getNode(state.addedId()).description)
    }

    @Test
    fun siblingAboveGoesRightBeforeTheSelectedNode() {
        val state = state()
        state.selectNode("c")

        state.addSiblingNode("D", isAbove = true)

        assertEquals(listOf("b", state.addedId(), "c"), state.tree.childrenOf("a"))
    }

    @Test
    fun siblingOfAChildOfTheRootIsOnItsSide() {
        val state = state()
        state.selectNode("e")

        state.addSiblingNode("F", side = LayoutSide.LOWER_RIGHT)

        val rootId = state.tree.getRootNode().id
        assertEquals(listOf("a", "e", state.addedId(), "g"), state.tree.childrenOf(rootId))
        assertEquals(LayoutSide.UPPER_LEFT, state.tree.sideOf(state.addedId()))
    }

    @Test
    fun siblingOfTheRootIsAChildOnTheSideGiven() {
        val state = state()
        val rootId = state.tree.getRootNode().id
        state.select(state.tree.getRootNode())

        state.addSiblingNode("H", side = LayoutSide.LOWER_LEFT)

        assertEquals(state.addedId(), state.tree.childrenOf(rootId).last())
        assertEquals(LayoutSide.LOWER_LEFT, state.tree.sideOf(state.addedId()))
    }

    @Test
    fun siblingTakesTheShapeAndColorsGiven() {
        val state = state()
        state.selectNode("b")

        state.addSiblingNode("D", NodeShape.OVAL, color = Color.Red, textColor = Color.Blue)

        val added = state.tree.getNode(state.addedId())
        assertIs<OvalNodeData>(added)
        assertEquals(Color.Red, added.color)
        assertEquals(Color.Blue, added.textColor)
    }

    @Test
    fun selectedNodeStaysSelected() {
        val state = state()
        state.selectNode("b")

        state.addSiblingNode("D")

        assertEquals("b", state.selectedNode?.id)
    }

    @Test
    fun addingASiblingIsOneStepToUndo() {
        val state = state()
        state.selectNode("b")
        assertFalse(state.canUndo)

        state.addSiblingNode("D")
        assertTrue(state.canUndo)
        state.undo()

        assertEquals(listOf("b", "c"), state.tree.childrenOf("a"))
        assertFalse(state.canUndo)
    }

    @Test
    fun withNothingSelectedNoSiblingIsAdded() {
        val state = state()
        state.select(null)

        state.addSiblingNode("D")

        assertFalse(state.canUndo)
        assertEquals(listOf("b", "c"), state.tree.childrenOf("a"))
    }
}
